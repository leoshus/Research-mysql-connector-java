###MySQL connector java 5.1.28
-----------------------------
###JDBC方式访问Database
-------------------
作为一个web码农，在我刚入门那会、做点儿小应用一般需要访问数据库的时候，都会写下如下代码

```
Class.forName("com.mysql.jdbc.Driver");
connection = DriverManager.getConnection(URL, USERNAME, PASSWORD);
PreparedStatement pstmt = connection.prepareStatement("select * from user");
	ResultSet rs = pstmt.executeQuery();
	if(rs.next()){
		logger.info("username:{}",rs.getString("username"));
	}
	亦或是
	PreparedStatement pstmt = connection.prepareStatement("insert into user (id,username,password,age,address,create_date) values('1','tony','admin','23','beijing',now())");
	int count = pstmt.executeUpdate();
	if(count > 0){
		logger.info("插入成功!插入"+count+"条数据!");
	}		
```
上述代码实际包含了两个步骤

####步骤一:装载驱动类Driver
-------------------------

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

####步骤二:根据Url、username、password获取connection对象
-----------------------------------------------------
通常URL格式为：jdbc:protocol://host_name:port/databaseName?param_name=param_value
例如mysql的url形式：
```
jdbc:mysql://127.0.0.1:3306/test?useUnicode=true&characterEncoding=GBK&jdbcCompliantTruncation=false
```
DriverManager中重载了4个getConnection方法：
```
public Connection getConnection(String url,java.util.Properties info)
public Connection getConnection(String url,String user, String password)
public Connection getConnection(String url)
private Connection getConnection(String url, java.util.Properties info, ClassLoader callerCL)
```
前三个重载方法都最终交给第四个方法处理,那么来看看这个DriverManager内部的私有方法

```
//  Worker method called by the public getConnection() methods.
    private static Connection getConnection(
	String url, java.util.Properties info, ClassLoader callerCL) throws SQLException {
	java.util.Vector drivers = null;
        /*
	 * When callerCl is null, we should check the application's
	 * (which is invoking this class indirectly)
	 * classloader, so that the JDBC driver class outside rt.jar
	 * can be loaded from here.
	 */
	 //如果callerCl为空,我们需要检查直接调用DriverManager的ClassLoader,
	 //这样在rt.jar以外的 JDBC驱动类就可以被加载了
	synchronized(DriverManager.class) {	 
	  // synchronize loading of the correct classloader.
	  if(callerCL == null) {
	      callerCL = Thread.currentThread().getContextClassLoader();
	   }    
	} 
	if(url == null) {
	    throw new SQLException("The url cannot be null", "08001");
	}
	println("DriverManager.getConnection(\"" + url + "\")");
	if (!initialized) {
	    initialize();//完成系统属性jdbc.drivers对用的驱动名的加载
	}
	//从readDrivers中获得可用的Driver
	synchronized (DriverManager.class){ 
            // use the readcopy of drivers
	    drivers = readDrivers;  
        }
	// Walk through the loaded drivers attempting to make a connection.
	// Remember the first exception that gets raised so we can reraise it.
	SQLException reason = null;
	for (int i = 0; i < drivers.size(); i++) {
	    DriverInfo di = (DriverInfo)drivers.elementAt(i);
	    // If the caller does not have permission to load the driver then 
	    // skip it.
	    if ( getCallerClass(callerCL, di.driverClassName ) != di.driverClass ) {
		println("    skipping: " + di);
		continue;
	    }
	    try {
		println("    trying " + di);
		//挨个遍历系统属性jdbc.drivers中的驱动类
		Connection result = di.driver.connect(url, info);
		if (result != null) {
		    // Success!
		    println("getConnection returning " + di);
		    return (result);
		}
	    } catch (SQLException ex) {
		if (reason == null) {
		    reason = ex;
		}
	    }
	}
	// if we got here nobody could connect.
	if (reason != null)    {
	    println("getConnection failed: " + reason);
	    throw reason;
	}
	println("getConnection: no suitable driver found for "+ url);
	throw new SQLException("No suitable driver found for "+ url, "08001");
    }
```
在这里我们也能看到initialize()方法，可见在DriverManager中只要是使用到Driver的地方,都会提前检查是否已经初始化，然后开始遍历所有的驱动,
直到找到一个可用的驱动,并返回Connection对象。当然如果没有找到合适的驱动 会抛出一个08001异常。

那么,这些驱动是如何给我们返回Connection对象的呢？

```
//url 你懂的 info为 连接时的一些配置参数 host、port、databaseName等
public java.sql.Connection connect(String url, Properties info)
			throws SQLException {
		if (url != null) {
			if (StringUtils.startsWithIgnoreCase(url, LOADBALANCE_URL_PREFIX)) {//"jdbc:mysql:loadbalance://"
				return connectLoadBalanced(url, info);
			} else if (StringUtils.startsWithIgnoreCase(url,
					REPLICATION_URL_PREFIX)) {//"jdbc:mysql:replication://"
				return connectReplicationConnection(url, info);
			}
		}
		Properties props = null;
		if ((props = parseURL(url, info)) == null) {
			return null;
		}
		if (!"1".equals(props.getProperty(NUM_HOSTS_PROPERTY_KEY))) {
			return connectFailover(url, info);
		}
		try {
			//这里将url和host、port、databaseName等配置参数作为参数 构造ConnectionImp对象
			Connection newConn = com.mysql.jdbc.ConnectionImpl.getInstance(
					host(props), port(props), props, database(props), url);
			return newConn;
		} catch (SQLException sqlEx) {
			// Don't wrap SQLExceptions, throw
			// them un-changed.
			throw sqlEx;
		} catch (Exception ex) {
			SQLException sqlEx = SQLError.createSQLException(Messages
					.getString("NonRegisteringDriver.17") //$NON-NLS-1$
					+ ex.toString()
					+ Messages.getString("NonRegisteringDriver.18"), //$NON-NLS-1$
					SQLError.SQL_STATE_UNABLE_TO_CONNECT_TO_DATASOURCE, null);
			sqlEx.initCause(ex);
			throw sqlEx;
		}
	}
```
连接Connection对象的构造方法:支持JDBC3以及Order than JDBC3 和JDBC4运行时建立连接
```
protected static Connection getInstance(String hostToConnectTo,
			int portToConnectTo, Properties info, String databaseToConnectTo,
			String url) throws SQLException {
		if (!Util.isJdbc4()) {
			return new ConnectionImpl(hostToConnectTo, portToConnectTo, info,
					databaseToConnectTo, url);
		}
		return (Connection) Util.handleNewInstance(JDBC_4_CONNECTION_CTOR,
				new Object[] {
							hostToConnectTo, Integer.valueOf(portToConnectTo), info,
							databaseToConnectTo, url }, null);
	}
```

JDBC4运行时建立连接，通过反射来建立ConnectionImpl对象 com.mysql.jdbc.JDBC4Connection继承了ConnectionImpl,所以JDBC4的建立连接最终还是调用的是ConnectionImpl的构造器

```
private static final Constructor<?> JDBC_4_CONNECTION_CTOR;
static {
		mapTransIsolationNameToValue = new HashMap<String, Integer>(8);
		mapTransIsolationNameToValue.put("READ-UNCOMMITED", TRANSACTION_READ_UNCOMMITTED);
		mapTransIsolationNameToValue.put("READ-UNCOMMITTED", TRANSACTION_READ_UNCOMMITTED);
		mapTransIsolationNameToValue.put("READ-COMMITTED", TRANSACTION_READ_COMMITTED);
		mapTransIsolationNameToValue.put("REPEATABLE-READ", TRANSACTION_REPEATABLE_READ);
		mapTransIsolationNameToValue.put("SERIALIZABLE", TRANSACTION_SERIALIZABLE);
		if (Util.isJdbc4()) {
			try {
				JDBC_4_CONNECTION_CTOR = Class.forName(
						"com.mysql.jdbc.JDBC4Connection").getConstructor(
						new Class[] { String.class, Integer.TYPE,
								Properties.class, String.class, String.class });
			} catch (SecurityException e) {
				throw new RuntimeException(e);
			} catch (NoSuchMethodException e) {
				throw new RuntimeException(e);
			} catch (ClassNotFoundException e) {
				throw new RuntimeException(e);
			}
		} else {
			JDBC_4_CONNECTION_CTOR = null;
		}
	}
```
到这里我们已经将连接的url中的host、port、databaseName、username、password、以及url后面的一堆参数等等都放进了连接的构造器中。
然后就开始了createNewIO()建立连接。

```

```
