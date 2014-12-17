####java.security.AccessController
----------------------------------
AccessController 用于与访问控制相关的操作和决定。
`java.security.AccessController`中的doPrivileged方法是一个静态的native方法

```
public static native <T> T doPrivileged(PrivilegedAction<T> action);
```