Class.forName(ClassName) 告诉ClassLoader装载对应ClassName的类  被加载的类中的静态块、构造器将会被调用 `即会被实例化`(注意这里只能使用Class.forName()而不能用ClassLoader.loadClass() 因为Class.forName()实际是
调用了forName0(className, true, ClassLoader.getCallerClassLoader());这里的第二个参数指定 加载的class是否需要初始化`MySQL的驱动注册时需要初始化的` 而ClassLoader.loadClass()
实际上是调用了loadClass(name, false) 这里的第二个参数是指定加载的类是否需要jvm进行link，我们都知道class加载需要经过加载(loading)、连接(linking)、初始化(initializing)的过程 
故loadClass()装载的类 为初始化)
如com.mysql.jdbc.Driver中就存在静态块：
```
//向DriverManager注册自己(Driver)
static {
	try {
		//JDBC规范中明确要求这个Driver类必须要想DriverManager注册自己，即任何一个JDBC驱动的Driver类的代码
		//都必须有类似java.sql.DriverManager.registerDriver(new Driver());功能的代码
		//因此如果我们要自定义一个JDBC驱动，那么我们必须实现java.sql.Driver接口，
		//并且在实现类中调用java.sql.DriverManager.registerDriver(new Driver());
		java.sql.DriverManager.registerDriver(new Driver());
	} catch (SQLException E) {
		throw new RuntimeException("Can't register driver!");
	}
}
```
这部分会调用java.sql.DriverManager.registerDriver(new Driver());来向DriverManager注册自己 这里注册保证了与实现无关。也即如果我们要想访问不同的数据库，那么我们只需注册
实现了java.sql.Driver接口的不同database 的Driver即可
```
public static synchronized void registerDriver(java.sql.Driver driver)
throws SQLException {
if (!initialized) {//判断是否已经初始化 默认false
    initialize();
}
DriverInfo di = new DriverInfo();
di.driver = driver;
di.driverClass = driver.getClass();
di.driverClassName = di.driverClass.getName();
// Not Required -- drivers.addElement(di);
writeDrivers.addElement(di); 
println("registerDriver: " + di);
/* update the read copy of drivers vector */
readDrivers = (java.util.Vector) writeDrivers.clone();

}
```
首先我们来看看initialize()的初始化动作
```
// Class initialization.
static void initialize() {
    if (initialized) {//是否已经初始化
        return;
    }
    initialized = true;
    loadInitialDrivers();
    println("JDBC DriverManager initialized");
}
//完成系统属性jdbc.drivers对用的驱动名的加载
private static void loadInitialDrivers() {
    String drivers;
    try {
    //使用了`java的安全许可`获取系统属性中的jdbc.drivers对应的驱动名
    //驱动名之间使用":"分隔开 
    drivers = (String) java.security.AccessController.doPrivileged(
	new sun.security.action.GetPropertyAction("jdbc.drivers"));
    } catch (Exception ex) {
        drivers = null;
    }
    // If the driver is packaged as a Service Provider,
    // load it.
    // Get all the drivers through the classloader 
    // exposed as a java.sql.Driver.class service.
    //实例化DriverManager的内部类DriverService
 DriverService ds = new DriverService();
 // Have all the privileges to get all the 
 // implementation of java.sql.Driver
 java.security.AccessController.doPrivileged(ds);		
     println("DriverManager.initialize: jdbc.drivers = " + drivers);
    if (drivers == null) {
        return;
    }
    while (drivers.length() != 0) {//drivers不为null
        int x = drivers.indexOf(':');
        String driver;
        if (x < 0) {
            driver = drivers;
            drivers = "";
        } else {//驱动名通过":"分隔
            driver = drivers.substring(0, x);//获取一个驱动名
            drivers = drivers.substring(x+1);//准备下一次循环
        }
        if (driver.length() == 0) {
            continue;
        }
        try {
            println("DriverManager.Initialize: loading " + driver);
            Class.forName(driver, true,
		      ClassLoader.getSystemClassLoader());//使用ClassLoader.getSystemClassLoader()加载驱动类
        } catch (Exception ex) {
            println("DriverManager.Initialize: load failed: " + ex);
        }
    }
}
```
到这里 初始化加载驱动类 节本完成。接下来	
```
DriverInfo di = new DriverInfo();
di.driver = driver;
di.driverClass = driver.getClass();
di.driverClassName = di.driverClass.getName();
```
实例化DriverInfo记录驱动的信息
```
// Not Required -- drivers.addElement(di);
writeDrivers.addElement(di); 
println("registerDriver: " + di);
/* update the read copy of drivers vector */
readDrivers = (java.util.Vector) writeDrivers.clone();
```
使用vector结构的writeDrivers保存驱动集合，然后克隆clone给同样是vector结构的readDrivers，这样做的目的是保证readDrivers中获得的驱动都是可用的
所以驱动的注册和注销是交给writeDrivers完成的，writeDrivers完成后再将自己clone给readDrivers，以供使用。从而达到读写分离的效果。

`注`:`java.security.AccessController.doPrivileged()`方法允许在一个类实例中的代码这个AccessController,它的代码主体享受特权(Privileged),
它不管这个请求是由什么代码所引发的，只是单独负责对它可得到的资源的访问请求。比如说，一个调用者在调用doPrivileged方法时，可被标识为特权。
AccessController做访问控制决策时，如果checkPermission方法遇到一个通过doPrivileged方法调用而被视为特权调用者，那么checkPermission方法不会作许可检查，
表示那个访问请求是被允许的，如果调用者没有许可，则会抛出一个异常。