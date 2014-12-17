####java.security.AccessController
----------------------------------

#####Java安全模型
----------------
简单来说，当类由类加载器ClassLoader加载到JVM，这些运行时的类会根据Java Policy文件的配置，被赋予不同的权限。当这些类要访问某些系统资源(例如刚打开的Socket、读取文件等等)
亦或者是执行某些敏感的操作(例如存取密码)时，Java的安全管理器的(java.lang.SecurityManager)的检查方法会被调用，检查这些类是否具有必要的权限来执行这些操作。
	* 策略 即系统安全策略，由用户或者管理员配置，用来配置执行代码的权限。运行时的 java.security.Policy 对象用来代表该策略文件。
	* 权限  Java 定义了层次结构的权限对象，所有权限对象的根类是 java.security.Permission。权限的定义涉及两个核心属性：目标（Target）与动作 (Action)。
	例如对于文件相关的权限定义，其目标就是文件或者目录，其动作包括：读，写，删除等。
	* 保护域 保护域可以理解为具有共同的权限集的类的集合。

java中的权限是赋予保护域而不是直接赋给类。
保护域下包含许多类 而权限则与保护域对应
#####AccessController
-------------------- 
`java.security.AccessController`中的doPrivileged方法是一个静态的native方法
```
public static native <T> T doPrivileged(PrivilegedAction<T> action);
```
用于与访问控制相关的操作和决定。
	* 基于当前生效的安全策略(xxx.policy)来决定是否允许关键系统资源的访问(例如，文件、socket等)
	* 将当前代码赋予特权，从而影响后续访问决定
	* 获取当前调用上下文的snapshot 利用已保存的上下文来决定其他上下文的访问控制

```
/**
 *基于当前的AccessControlContext上下文 和安全策略 (security policy)来确定是否拒绝由特定权限所指示的访问请求
 *如果访问请求被允许则正常返回，否则会抛出AccessControlException
 **/
public static void checkPermission(Permission perm)
		 throws AccessControlException 
    {
	//System.err.println("checkPermission "+perm);
	//Thread.currentThread().dumpStack();

	if (perm == null) {
	    throw new NullPointerException("permission can't be null");
	}
	AccessControlContext stack = getStackAccessControlContext();
	// if context is null, we had privileged system code on the stack.
	if (stack == null) {
	    Debug debug = AccessControlContext.getDebug();
	    boolean dumpDebug = false;
	    if (debug != null) {
		dumpDebug = !Debug.isOn("codebase=");
		dumpDebug &= !Debug.isOn("permission=") ||
		    Debug.isOn("permission=" + perm.getClass().getCanonicalName());
	    }
	    if (dumpDebug && Debug.isOn("stack")) {
		Thread.currentThread().dumpStack();
	    }
	    if (dumpDebug && Debug.isOn("domain")) {
		debug.println("domain (context is null)");
	    }
	    if (dumpDebug) {
		debug.println("access allowed "+perm);
	    }
	    return;
	}
	AccessControlContext acc = stack.optimize();
	acc.checkPermission(perm);
    }
```
例如
```
FilePermission perm = new FilePermission("/temp/testFile", "read");
AccessController.checkPermission(perm);
```
checkPermission决定批准还是拒绝对目录`/temp/testFile`下文件的读取 若允许则正常返回 否则会抛出AccessControlException
可以将调用方标记为享有“特权”（请参阅 doPrivileged 及下文）。在做访问控制决定时，如果遇到通过调用不带上下文参数的 doPrivileged 标记为“特权”的调用方
则 checkPermission 方法将停止检查。
```
somemethod() {
      ...normal code here...
      AccessController.doPrivileged(new PrivilegedAction() {
             public Object run() {
                    // privileged code goes here, for example:
                    System.loadLibrary("awt");
                    return null; // nothing to return
            }
      });
      ...normal code here...
} 
```
PrivilegedAction 是一个具有单个方法的接口，该方法名为 run 并返回一个 Object。上述示例显示该接口的实现的创建；提供了 run 方法的具体实现。
调用 doPrivileged 时，将 PrivilegedAction 实现的实例传递给它。doPrivileged 方法在启用特权后从 PrivilegedAction 实现调用 run 方法，
并返回 run 方法的返回值作为 doPrivileged 返回值（在此示例中忽略）。
如果需要返回值，则可使用以下代码：

```
somemethod() {
        ...normal code here...
        String user = (String) AccessController.doPrivileged(new PrivilegedAction() {
                  public Object run() {
                         return System.getProperty("user.name");
                 }
        });
        ...normal code here...
}
```
如果在 run 方法中执行的操作可以抛出“已检查”异常（列在方法的 throws 子句中），则需要使用 PrivilegedExceptionAction 接口代替 PrivilegedAction 接口：
```
somemethod() throws FileNotFoundException {
        ...normal code here...
        try {
               FileInputStream fis = (FileInputStream)
               AccessController.doPrivileged(new PrivilegedExceptionAction() {
                     public Object run() throws FileNotFoundException {
                            return new FileInputStream("someFile");
                     }
               });
        } catch (PrivilegedActionException e) {
                 // e.getException() should be an instance of
                 // FileNotFoundException,
                 // as only "checked" exceptions will be "wrapped" in a
                 // PrivilegedActionException.
                throw (FileNotFoundException) e.getException();
        }
       ...normal code here...
}
```
有关被授予特权的一些重要事项：
首先，这个概念仅存在于一个单独线程内。一旦特权代码完成了任务，特权将被保证清除或作废。
第二，在这个例子中，在run方法中的代码体被授予了特权。然而，如果它调用无特权的不可信代码，则那个代码将不会获得任何特权；
只有在特权代码具有许可并且在直到checkPermission调用的调用链中的所有随后的调用者也具有许可时, 一个许可才能被准予。

****************************************************************************************************

在使用“特权”构造时务必 * 特别 * 小心，始终让享有特权的代码段尽可能小。
注意，checkPermission 始终在当前执行线程的上下文中执行安全性检查。有时，本来应该在给定上下文中进行的安全性检查实际需要在另一个 上下文中（
例如，在 worker 线程中）完成。getContext 方法和 AccessControlContext 类是针对这种情况提供的。getContext 方法获取当前调用上下文的“快照”，
并将其置于它所返回的 AccessControlContext 对象中。示例调用如下：
```
  AccessControlContext acc = AccessController.getContext()
```
AccessControlContext 本身具有一个 checkPermission 方法，该方法基于它 所封装的上下文而不是当前执行线程作出访问决定。因此，另一上下文中的代码
可以在以前保存的 AccessControlContext 对象上调用该方法。示例调用如下：
```
acc.checkPermission(permission)
```

