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
			if (StringUtils.startsWithIgnoreCase(url, LOADBALANCE_URL_PREFIX)) {//"jdbc:mysql:loadbalance://"负载均衡的配置
				return connectLoadBalanced(url, info);
			} else if (StringUtils.startsWithIgnoreCase(url,
					REPLICATION_URL_PREFIX)) {//"jdbc:mysql:replication://" 复制
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
			//这里将url和host、port、databaseName等配置参数作为参数 构造ConnectionImp对象 即初始化连接
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
		//反射调用ConnectionImpl的构造器
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
	protected ConnectionImpl(String hostToConnectTo, int portToConnectTo, Properties info,
			String databaseToConnectTo, String url)
			throws SQLException {
		this.connectionCreationTimeMillis = System.currentTimeMillis();
		if (databaseToConnectTo == null) {
			databaseToConnectTo = "";
		}
		// Stash away for later, used to clone this connection for Statement.cancel
		// and Statement.setQueryTimeout().
		//
		this.origHostToConnectTo = hostToConnectTo;
		this.origPortToConnectTo = portToConnectTo;
		this.origDatabaseToConnectTo = databaseToConnectTo;
		try {
			Blob.class.getMethod("truncate", new Class[] {Long.TYPE});
			this.isRunningOnJDK13 = false;
		} catch (NoSuchMethodException nsme) {
			this.isRunningOnJDK13 = true;
		}
		this.sessionCalendar = new GregorianCalendar();
		this.utcCalendar = new GregorianCalendar();
		this.utcCalendar.setTimeZone(TimeZone.getTimeZone("GMT"));
		//
		// Normally, this code would be in initializeDriverProperties,
		// but we need to do this as early as possible, so we can start
		// logging to the 'correct' place as early as possible...this.log
		// points to 'NullLogger' for every connection at startup to avoid
		// NPEs and the overhead of checking for NULL at every logging call.
		//
		// We will reset this to the configured logger during properties
		// initialization.
		//
		this.log = LogFactory.getLogger(getLogger(), LOGGER_INSTANCE_NAME, getExceptionInterceptor());
		// We store this per-connection, due to static synchronization
		// issues in Java's built-in TimeZone class...
		this.defaultTimeZone = Util.getDefaultTimeZone();
		if ("GMT".equalsIgnoreCase(this.defaultTimeZone.getID())) {
			this.isClientTzUTC = true;
		} else {
			this.isClientTzUTC = false;
		}
		this.openStatements = new HashMap<Statement, Statement>();
		if (NonRegisteringDriver.isHostPropertiesList(hostToConnectTo)) {
			//将address=(protocol=tcp)(host=localhost)(port=3306)形式的数据转换成java.util.Properties
			//其中的特殊字符() and = 需要用引号引起来
			Properties hostSpecificProps = NonRegisteringDriver.expandHostKeyValues(hostToConnectTo);
			Enumeration<?> propertyNames = hostSpecificProps.propertyNames();
			while (propertyNames.hasMoreElements()) {
				String propertyName = propertyNames.nextElement().toString();
				String propertyValue = hostSpecificProps.getProperty(propertyName);
				info.setProperty(propertyName, propertyValue);
			}
		} else {
			if (hostToConnectTo == null) {
				this.host = "localhost";
				this.hostPortPair = this.host + ":" + portToConnectTo;
			} else {
				this.host = hostToConnectTo;
				if (hostToConnectTo.indexOf(":") == -1) {
					this.hostPortPair = this.host + ":" + portToConnectTo;
				} else {
					this.hostPortPair = this.host;
				}
			}
		}
		this.port = portToConnectTo;
		this.database = databaseToConnectTo;
		this.myURL = url;
		this.user = info.getProperty(NonRegisteringDriver.USER_PROPERTY_KEY);
		this.password = info
				.getProperty(NonRegisteringDriver.PASSWORD_PROPERTY_KEY);
		if ((this.user == null) || this.user.equals("")) {
			this.user = "";
		}
		if (this.password == null) {
			this.password = "";
		}
		this.props = info;
		initializeDriverProperties(info);
		if (getUseUsageAdvisor()) {
			this.pointOfOrigin = LogUtils.findCallingClassAndMethod(new Throwable());
		} else {
			this.pointOfOrigin = "";
		}
		try {
			this.dbmd = getMetaData(false, false);//获取数据库元数据
			initializeSafeStatementInterceptors();
			createNewIO(false);//建立远程IO连接
			unSafeStatementInterceptors();
		} catch (SQLException ex) {
			cleanup(ex);
			// don't clobber SQL exceptions
			throw ex;
		} catch (Exception ex) {
			cleanup(ex);
			StringBuffer mesg = new StringBuffer(128);
			if (!getParanoid()) {
				mesg.append("Cannot connect to MySQL server on ");
				mesg.append(this.host);
				mesg.append(":");
				mesg.append(this.port);
				mesg.append(".\n\n");
				mesg.append("Make sure that there is a MySQL server ");
				mesg.append("running on the machine/port you are trying ");
				mesg
						.append("to connect to and that the machine this software is "
								+ "running on ");
				mesg.append("is able to connect to this host/port "
						+ "(i.e. not firewalled). ");
				mesg
						.append("Also make sure that the server has not been started "
								+ "with the --skip-networking ");
				mesg.append("flag.\n\n");
			} else {
				mesg.append("Unable to connect to database.");
			}
			SQLException sqlEx = SQLError.createSQLException(mesg.toString(),
					SQLError.SQL_STATE_COMMUNICATION_LINK_FAILURE, getExceptionInterceptor());
			sqlEx.initCause(ex);
			throw sqlEx;
		}
		NonRegisteringDriver.trackConnection(this);
	}
```
到这里我们已经将连接的url中的host、port、databaseName、username、password、以及url后面的一堆参数等等都放进了连接的构造器中。
然后就开始了createNewIO()建立连接。

#####远程IO创建
---------------

```
public void createNewIO(boolean isForReconnect)
			throws SQLException {
		synchronized (getConnectionMutex()) {
			// Synchronization Not needed for *new* connections, but defintely for
			// connections going through fail-over, since we might get the
			// new connection up and running *enough* to start sending
			// cached or still-open server-side prepared statements over
			// to the backend before we get a chance to re-prepare them...
			Properties mergedProps  = exposeAsProperties(this.props);
			if (!getHighAvailability()) {
				connectOneTryOnly(isForReconnect, mergedProps);
				return;
			} 
			connectWithRetries(isForReconnect, mergedProps);
		}		
	}
	private void connectOneTryOnly(boolean isForReconnect,
			Properties mergedProps) throws SQLException {
		Exception connectionNotEstablishedBecause = null;
		try {
			coreConnect(mergedProps);//IO核心创建方法
			this.connectionId = this.io.getThreadId();
			this.isClosed = false;
			// save state from old connection
			boolean oldAutoCommit = getAutoCommit();
			int oldIsolationLevel = this.isolationLevel;
			boolean oldReadOnly = isReadOnly(false);
			String oldCatalog = getCatalog();
			this.io.setStatementInterceptors(this.statementInterceptors);
			// Server properties might be different
			// from previous connection, so initialize
			// again...
			initializePropsFromServer();
			if (isForReconnect) {
				// Restore state from old connection
				setAutoCommit(oldAutoCommit);
				if (this.hasIsolationLevels) {
					setTransactionIsolation(oldIsolationLevel);
				}
				setCatalog(oldCatalog);
				setReadOnly(oldReadOnly);
			}
			return;
		} catch (Exception EEE) {
			if (EEE instanceof SQLException
					&& ((SQLException)EEE).getErrorCode() == MysqlErrorNumbers.ER_MUST_CHANGE_PASSWORD
					&& !getDisconnectOnExpiredPasswords()) {
				return;
			}
			if (this.io != null) {
				this.io.forceClose();
			}
			connectionNotEstablishedBecause = EEE;
			if (EEE instanceof SQLException) {
				throw (SQLException)EEE;
			}
			SQLException chainedEx = SQLError.createSQLException(
					Messages.getString("Connection.UnableToConnect"),
					SQLError.SQL_STATE_UNABLE_TO_CONNECT_TO_DATASOURCE, getExceptionInterceptor());
			chainedEx.initCause(connectionNotEstablishedBecause);
			throw chainedEx;
		}
	}
```

#####IO创建核心方法
------------------
```
private void coreConnect(Properties mergedProps) throws SQLException,
			IOException {
		int newPort = 3306;
		String newHost = "localhost";
		String protocol = mergedProps.getProperty(NonRegisteringDriver.PROTOCOL_PROPERTY_KEY);
		if (protocol != null) {
			// "new" style URL
			if ("tcp".equalsIgnoreCase(protocol)) {
				newHost = normalizeHost(mergedProps.getProperty(NonRegisteringDriver.HOST_PROPERTY_KEY));
				newPort = parsePortNumber(mergedProps.getProperty(NonRegisteringDriver.PORT_PROPERTY_KEY, "3306"));
			} else if ("pipe".equalsIgnoreCase(protocol)) {
				setSocketFactoryClassName(NamedPipeSocketFactory.class.getName());
				String path = mergedProps.getProperty(NonRegisteringDriver.PATH_PROPERTY_KEY);
				if (path != null) {
					mergedProps.setProperty(NamedPipeSocketFactory.NAMED_PIPE_PROP_NAME, path);
				}
			} else {
				// normalize for all unknown protocols
				newHost = normalizeHost(mergedProps.getProperty(NonRegisteringDriver.HOST_PROPERTY_KEY));
				newPort = parsePortNumber(mergedProps.getProperty(NonRegisteringDriver.PORT_PROPERTY_KEY, "3306"));
			}
		} else {
			String[] parsedHostPortPair = NonRegisteringDriver
					.parseHostPortPair(this.hostPortPair);
			newHost = parsedHostPortPair[NonRegisteringDriver.HOST_NAME_INDEX];
			newHost = normalizeHost(newHost);
			if (parsedHostPortPair[NonRegisteringDriver.PORT_NUMBER_INDEX] != null) {
				newPort = parsePortNumber(parsedHostPortPair[NonRegisteringDriver.PORT_NUMBER_INDEX]);
			}
		}
		this.port = newPort;
		this.host = newHost;
		//构建MysqlIO用于与服务器进行通信
		this.io = new MysqlIO(newHost, newPort,
				mergedProps, getSocketFactoryClassName(),
				getProxy(), getSocketTimeout(),
				this.largeRowSizeThreshold.getValueAsInt());
				//与Mysql服务器进行握手
		this.io.doHandshake(this.user, this.password,
				this.database);
	}
```
#####MysqlIO 构造器
------------------
```
public MysqlIO(String host, int port, Properties props,
        String socketFactoryClassName, MySQLConnection conn,
        int socketTimeout, int useBufferRowSizeThreshold) throws IOException, SQLException {
        this.connection = conn;
        if (this.connection.getEnablePacketDebug()) {
            this.packetDebugRingBuffer = new LinkedList<StringBuffer>();
        }
        this.traceProtocol = this.connection.getTraceProtocol();
        this.useAutoSlowLog = this.connection.getAutoSlowLog();
        this.useBufferRowSizeThreshold = useBufferRowSizeThreshold;
        this.useDirectRowUnpack = this.connection.getUseDirectRowUnpack();
        this.logSlowQueries = this.connection.getLogSlowQueries();
        this.reusablePacket = new Buffer(INITIAL_PACKET_SIZE);
        this.sendPacket = new Buffer(INITIAL_PACKET_SIZE);
        this.port = port;
        this.host = host;
        this.socketFactoryClassName = socketFactoryClassName;
        this.socketFactory = createSocketFactory();
        this.exceptionInterceptor = this.connection.getExceptionInterceptor();
        try {
        	this.mysqlConnection = this.socketFactory.connect(this.host,
        		this.port, props);//利用com.mysql.jdbc.StandardSocketFactory创建一个Socket 来与Mysql服务器进行握手(doHandshake())
	        if (socketTimeout != 0) {
	        	try {
	        		this.mysqlConnection.setSoTimeout(socketTimeout);
	        	} catch (Exception ex) {
	        		/* Ignore if the platform does not support it */
	        	}
	        }
	        this.mysqlConnection = this.socketFactory.beforeHandshake();
	        if (this.connection.getUseReadAheadInput()) {
	        	//创建input流
	        	this.mysqlInput = new ReadAheadInputStream(this.mysqlConnection.getInputStream(), 16384,
	        			this.connection.getTraceProtocol(),
	        			this.connection.getLog());
	        } else if (this.connection.useUnbufferedInput()) {
	        	this.mysqlInput = this.mysqlConnection.getInputStream();
	        } else {
	        	this.mysqlInput = new BufferedInputStream(this.mysqlConnection.getInputStream(),
	        			16384);
	        }
	      	//创建output流
	        this.mysqlOutput = new BufferedOutputStream(this.mysqlConnection.getOutputStream(),
	        		16384);
	        this.isInteractiveClient = this.connection.getInteractiveClient();
	        this.profileSql = this.connection.getProfileSql();
	        this.autoGenerateTestcaseScript = this.connection.getAutoGenerateTestcaseScript();
	        this.needToGrabQueryFromPacket = (this.profileSql ||
	        		this.logSlowQueries ||
	        		this.autoGenerateTestcaseScript);
	        if (this.connection.getUseNanosForElapsedTime()
					&& Util.nanoTimeAvailable()) {
				this.useNanosForElapsedTime = true;
				this.queryTimingUnits = Messages.getString("Nanoseconds");
			} else {
				this.queryTimingUnits = Messages.getString("Milliseconds");
			}
			if (this.connection.getLogSlowQueries()) {
				calculateSlowQueryThreshold();
			}
        } catch (IOException ioEx) {
        	throw SQLError.createCommunicationsException(this.connection, 0, 0, ioEx, getExceptionInterceptor());
        }
    }
```

#####与Mysql服务器握手
-------------
回到coreConnect()方法的最后，我们会看到`this.io.doHandshake(this.user, this.password,this.database);`doHandshake()方法主要用来初始化与Mysql server的连接,负责登录服务器和处理连接错误。其中会分析所连接的Mysql版本，可以根据Mysql版本以及以及是否使用SSL加密数据都有不同的处理方式,并把传送给database的数据放在一个叫`packet`的buffer中，调用send()方法往outputStream中写入要发送的数据。

```
void doHandshake(String user, String password, String database)
        throws SQLException {
        // Read the first packet
        this.checkPacketSequence = false;
        this.readPacketSequence = 0;
        Buffer buf = readPacket();
        // Get the protocol version
        this.protocolVersion = buf.readByte();
        if (this.protocolVersion == -1) {
            try {
                this.mysqlConnection.close();
            } catch (Exception e) {
                // ignore
            }
            int errno = 2000;
            errno = buf.readInt();
            String serverErrorMessage = buf.readString("ASCII", getExceptionInterceptor());
            StringBuffer errorBuf = new StringBuffer(Messages.getString(
                        "MysqlIO.10")); //$NON-NLS-1$
            errorBuf.append(serverErrorMessage);
            errorBuf.append("\""); //$NON-NLS-1$
            String xOpen = SQLError.mysqlToSqlState(errno,
                    this.connection.getUseSqlStateCodes());
            throw SQLError.createSQLException(SQLError.get(xOpen) + ", " //$NON-NLS-1$
                 +errorBuf.toString(), xOpen, errno, getExceptionInterceptor());
        }
        this.serverVersion = buf.readString("ASCII", getExceptionInterceptor());
        // Parse the server version into major/minor/subminor
        int point = this.serverVersion.indexOf('.'); //$NON-NLS-1$
        if (point != -1) {
            try {
                int n = Integer.parseInt(this.serverVersion.substring(0, point));
                this.serverMajorVersion = n;
            } catch (NumberFormatException NFE1) {
                // ignore
            }
            String remaining = this.serverVersion.substring(point + 1,
                    this.serverVersion.length());
            point = remaining.indexOf('.'); //$NON-NLS-1$
            if (point != -1) {
                try {
                    int n = Integer.parseInt(remaining.substring(0, point));
                    this.serverMinorVersion = n;
                } catch (NumberFormatException nfe) {
                    // ignore
                }
                remaining = remaining.substring(point + 1, remaining.length());
                int pos = 0;
                while (pos < remaining.length()) {
                    if ((remaining.charAt(pos) < '0') ||
                            (remaining.charAt(pos) > '9')) {
                        break;
                    }
                    pos++;
                }
                try {
                    int n = Integer.parseInt(remaining.substring(0, pos));
                    this.serverSubMinorVersion = n;
                } catch (NumberFormatException nfe) {
                    // ignore
                }
            }
        }
        if (versionMeetsMinimum(4, 0, 8)) {
            this.maxThreeBytes = (256 * 256 * 256) - 1;
            this.useNewLargePackets = true;
        } else {
            this.maxThreeBytes = 255 * 255 * 255;
            this.useNewLargePackets = false;
        }
        this.colDecimalNeedsBump = versionMeetsMinimum(3, 23, 0);
        this.colDecimalNeedsBump = !versionMeetsMinimum(3, 23, 15); // guess? Not noted in changelog
        this.useNewUpdateCounts = versionMeetsMinimum(3, 22, 5);
        // read connection id
        threadId = buf.readLong();
        if (this.protocolVersion > 9) {
            // read auth-plugin-data-part-1 (string[8])
            this.seed = buf.readString("ASCII", getExceptionInterceptor(), 8);
            // read filler ([00])
            buf.readByte();
        } else {
        	// read scramble (string[NUL])
            this.seed = buf.readString("ASCII", getExceptionInterceptor());
        }
        this.serverCapabilities = 0;
        // read capability flags (lower 2 bytes)
        if (buf.getPosition() < buf.getBufLength()) {
            this.serverCapabilities = buf.readInt();
        }
        if ((versionMeetsMinimum(4, 1, 1) || ((this.protocolVersion > 9) && (this.serverCapabilities & CLIENT_PROTOCOL_41) != 0))) {
            /* New protocol with 16 bytes to describe server characteristics */
            // read character set (1 byte)
            this.serverCharsetIndex = buf.readByte() & 0xff;
            // read status flags (2 bytes)
            this.serverStatus = buf.readInt();
            checkTransactionState(0);
            // read capability flags (upper 2 bytes)
           	this.serverCapabilities |= buf.readInt() << 16;
           	if ((this.serverCapabilities & CLIENT_PLUGIN_AUTH) != 0) {
               	// read length of auth-plugin-data (1 byte)
               	this.authPluginDataLength = buf.readByte() & 0xff;
           	} else {
           		// read filler ([00])
           		buf.readByte();
           	}
            // next 10 bytes are reserved (all [00])
           	buf.setPosition(buf.getPosition() + 10);
           	if ((this.serverCapabilities & CLIENT_SECURE_CONNECTION) != 0) {
           		String seedPart2;
           		StringBuffer newSeed;
            	// read string[$len] auth-plugin-data-part-2 ($len=MAX(13, length of auth-plugin-data - 8))
           		if (this.authPluginDataLength > 0) {
// TODO: disabled the following check for further clarification
//         			if (this.authPluginDataLength < 21) {
//                      forceClose();
//                      throw SQLError.createSQLException(Messages.getString("MysqlIO.103"), //$NON-NLS-1$
//                          SQLError.SQL_STATE_UNABLE_TO_CONNECT_TO_DATASOURCE, getExceptionInterceptor());
//         			}
                    seedPart2 = buf.readString("ASCII", getExceptionInterceptor(), this.authPluginDataLength - 8);
                    newSeed = new StringBuffer(this.authPluginDataLength);
           		} else {
           			seedPart2 = buf.readString("ASCII", getExceptionInterceptor());
                    newSeed = new StringBuffer(20);
           		}
                newSeed.append(this.seed);
                newSeed.append(seedPart2);
                this.seed = newSeed.toString();
           	}
        }
        if (((this.serverCapabilities & CLIENT_COMPRESS) != 0) &&
                this.connection.getUseCompression()) {
            this.clientParam |= CLIENT_COMPRESS;
        }
        	this.useConnectWithDb = (database != null) &&
			(database.length() > 0) &&
			!this.connection.getCreateDatabaseIfNotExist();
        if (this.useConnectWithDb) {
            this.clientParam |= CLIENT_CONNECT_WITH_DB;
        }
        if (((this.serverCapabilities & CLIENT_SSL) == 0) &&
                this.connection.getUseSSL()) {
            if (this.connection.getRequireSSL()) {
                this.connection.close();
                forceClose();
                throw SQLError.createSQLException(Messages.getString("MysqlIO.15"), //$NON-NLS-1$
                    SQLError.SQL_STATE_UNABLE_TO_CONNECT_TO_DATASOURCE, getExceptionInterceptor());
            }
            this.connection.setUseSSL(false);
        }
        if ((this.serverCapabilities & CLIENT_LONG_FLAG) != 0) {
            // We understand other column flags, as well
            this.clientParam |= CLIENT_LONG_FLAG;
            this.hasLongColumnInfo = true;
        }
        // return FOUND rows
        if (!this.connection.getUseAffectedRows()) {
        	this.clientParam |= CLIENT_FOUND_ROWS;
        }
        if (this.connection.getAllowLoadLocalInfile()) {
            this.clientParam |= CLIENT_LOCAL_FILES;
        }
        if (this.isInteractiveClient) {
            this.clientParam |= CLIENT_INTERACTIVE;
        }
        //
        // switch to pluggable authentication if available
        //
    	if ((this.serverCapabilities & CLIENT_PLUGIN_AUTH) != 0) {
            proceedHandshakeWithPluggableAuthentication(user, password, database, buf);
            return;
        }
        // Authenticate
        if (this.protocolVersion > 9) {
            this.clientParam |= CLIENT_LONG_PASSWORD; // for long passwords
        } else {
            this.clientParam &= ~CLIENT_LONG_PASSWORD;
        }
        //
        // 4.1 has some differences in the protocol
        //
        if ((versionMeetsMinimum(4, 1, 0) || ((this.protocolVersion > 9) && (this.serverCapabilities & CLIENT_RESERVED) != 0))) {
            if ((versionMeetsMinimum(4, 1, 1) || ((this.protocolVersion > 9) && (this.serverCapabilities & CLIENT_PROTOCOL_41) != 0))) {
                this.clientParam |= CLIENT_PROTOCOL_41;
                this.has41NewNewProt = true;
                // Need this to get server status values
                this.clientParam |= CLIENT_TRANSACTIONS;
                // We always allow multiple result sets
                this.clientParam |= CLIENT_MULTI_RESULTS;
                // We allow the user to configure whether
                // or not they want to support multiple queries
                // (by default, this is disabled).
                if (this.connection.getAllowMultiQueries()) {
                    this.clientParam |= CLIENT_MULTI_STATEMENTS;
                }
            } else {
                this.clientParam |= CLIENT_RESERVED;
                this.has41NewNewProt = false;
            }
            this.use41Extensions = true;
        }
        int passwordLength = 16;
        int userLength = (user != null) ? user.length() : 0;
        int databaseLength = (database != null) ? database.length() : 0;
        int packLength = ((userLength + passwordLength + databaseLength) * 3) + 7 + HEADER_LENGTH + AUTH_411_OVERHEAD;
        Buffer packet = null;
        if (!this.connection.getUseSSL()) {
            if ((this.serverCapabilities & CLIENT_SECURE_CONNECTION) != 0) {
                this.clientParam |= CLIENT_SECURE_CONNECTION;
                if ((versionMeetsMinimum(4, 1, 1) || ((this.protocolVersion > 9) && (this.serverCapabilities & CLIENT_PROTOCOL_41) != 0))) {
                    secureAuth411(null, packLength, user, password, database,
                        true);
                } else {
                    secureAuth(null, packLength, user, password, database, true);
                }
            } else {
                // Passwords can be 16 chars long
                packet = new Buffer(packLength);
                if ((this.clientParam & CLIENT_RESERVED) != 0) {
                    if ((versionMeetsMinimum(4, 1, 1) || ((this.protocolVersion > 9) && (this.serverCapabilities & CLIENT_PROTOCOL_41) != 0))) {
                        packet.writeLong(this.clientParam);
                        packet.writeLong(this.maxThreeBytes);
                        // charset, JDBC will connect as 'latin1',
                        // and use 'SET NAMES' to change to the desired
                        // charset after the connection is established.
                        packet.writeByte((byte) 8);
                        // Set of bytes reserved for future use.
                        packet.writeBytesNoNull(new byte[23]);
                    } else {
                        packet.writeLong(this.clientParam);
                        packet.writeLong(this.maxThreeBytes);
                    }
                } else {
                    packet.writeInt((int) this.clientParam);
                    packet.writeLongInt(this.maxThreeBytes);
                }
                // User/Password data
                packet.writeString(user, CODE_PAGE_1252, this.connection);
                if (this.protocolVersion > 9) {
                    packet.writeString(Util.newCrypt(password, this.seed), CODE_PAGE_1252, this.connection);
                } else {
                    packet.writeString(Util.oldCrypt(password, this.seed), CODE_PAGE_1252, this.connection);
                }
                if (this.useConnectWithDb) {
                    packet.writeString(database, CODE_PAGE_1252, this.connection);
                }
                send(packet, packet.getPosition());
            }
        } else {
            negotiateSSLConnection(user, password, database, packLength);
            if ((this.serverCapabilities & CLIENT_SECURE_CONNECTION) != 0) {
                if (versionMeetsMinimum(4, 1, 1)) {
                    secureAuth411(null, packLength, user, password, database, true);
                } else {
                    secureAuth411(null, packLength, user, password, database, true);
                }
            } else {
            	packet = new Buffer(packLength);//保存要发送给Database的数据
                if (this.use41Extensions) {
                    packet.writeLong(this.clientParam);
                    packet.writeLong(this.maxThreeBytes);
                } else {
                    packet.writeInt((int) this.clientParam);
                    packet.writeLongInt(this.maxThreeBytes);
                }
                // User/Password data
                packet.writeString(user);
                if (this.protocolVersion > 9) {
                    packet.writeString(Util.newCrypt(password, this.seed));
                } else {
                    packet.writeString(Util.oldCrypt(password, this.seed));
                }
                if (((this.serverCapabilities & CLIENT_CONNECT_WITH_DB) != 0) &&
                        (database != null) && (database.length() > 0)) {
                    packet.writeString(database);
                }
                send(packet, packet.getPosition());//发送给Database数据
            }
        }
        // Check for errors, not for 4.1.1 or newer,
        // as the new auth protocol doesn't work that way
        // (see secureAuth411() for more details...)
        //if (!versionMeetsMinimum(4, 1, 1)) {
        if (!(versionMeetsMinimum(4, 1, 1) || !((this.protocolVersion > 9) && (this.serverCapabilities & CLIENT_PROTOCOL_41) != 0))) {
            checkErrorPacket();
        }
        //
        // Can't enable compression until after handshake
        //
        if (((this.serverCapabilities & CLIENT_COMPRESS) != 0) &&
                this.connection.getUseCompression()) {
            // The following matches with ZLIB's
            // compress()
            this.deflater = new Deflater();
            this.useCompression = true;
            this.mysqlInput = new CompressedInputStream(this.connection,
                    this.mysqlInput);
        }
        if (!this.useConnectWithDb) {
            changeDatabaseTo(database);
        }
        try {
        	this.mysqlConnection = this.socketFactory.afterHandshake();
        } catch (IOException ioEx) {
        	throw SQLError.createCommunicationsException(this.connection, this.lastPacketSentTimeMs, this.lastPacketReceivedTimeMs, ioEx, getExceptionInterceptor());
        }
    }
```

至此,我们已经完成Mysql JDBC 驱动的注册并获得了连接、建立了与Mysql进行handshake的远程IO连接。接下来我们就可以创建statement进行CRUD操作了。


***********************************************************************************************************************