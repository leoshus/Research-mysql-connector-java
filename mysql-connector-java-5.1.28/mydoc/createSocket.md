#####远程IO创建
---------------
在构造ConnectionImpl对象的构造器中我们会调用`createNewIO(false)`来建立一个远程IO来与Mysql server交互

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