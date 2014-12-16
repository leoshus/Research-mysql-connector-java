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

