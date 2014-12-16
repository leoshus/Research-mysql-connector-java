到目前为止,我们已经建立了Connection对象,接下来我们需要进行CRUD操作，就需要创建Statement对象，Statement有多种，我们常用的是PreparedStatement用于执行预编译好的SQL语句，CallableStatement用于调用数据库的存储过程
createStatement在`com.mysql.jdbc.ConnectionImpl`中存在三个重载方法。

```
public java.sql.Statement createStatement() throws SQLException 
public java.sql.Statement createStatement(int resultSetType,int resultSetConcurrency) throws SQLException
public java.sql.Statement createStatement(int resultSetType,int resultSetConcurrency, int resultSetHoldability)throws SQLException

```

我们常用的PreparedStatement在`com.mysql.jdbc.ConnectionImpl`中也存在多个重载方法。

```
/**
	 * sql不管是否带有输入参数 都将被预编译并保存在PreparedStatement对象中
	 * 在之后的使用的时候可以被高效地多次被执行
	 * @param sql sql语句中可能包含一个或者多个'?'作为参数的占位符
	 * @return 返回一个包含预编译sql语句的PreparedStatement对象
	 */
	public java.sql.PreparedStatement prepareStatement(String sql)
			throws SQLException {
		return prepareStatement(sql, DEFAULT_RESULT_SET_TYPE,
				DEFAULT_RESULT_SET_CONCURRENCY);
	}
	/**
	 * JDBC 2.0规范与上面的prepareStatement()相同,但是允许默认的resultSetType和resultSetConcurrencyType被重写
	 * @param sql 包含占位符的sql语句
	 * @param resultSetType see ResultSet.TYPE_XXX
	 * @param resultSetConcurrency see ResultSet.CONCUR_XXX
	 * @return 返回一个包含预编译SQL语句的PreparedStatement对象
	 */
	public java.sql.PreparedStatement prepareStatement(String sql,
			int resultSetType, int resultSetConcurrency) throws SQLException {
		synchronized (getConnectionMutex()) {
			checkClosed();
			//
			// FIXME: Create warnings if can't create results of the given
			// type or concurrency
			//
			PreparedStatement pStmt = null;
			boolean canServerPrepare = true;
			String nativeSql = getProcessEscapeCodesForPrepStmts() ? nativeSQL(sql): sql;
			if (this.useServerPreparedStmts && getEmulateUnsupportedPstmts()) {
				canServerPrepare = canHandleAsServerPreparedStatement(nativeSql);
			}
			//this.useServerPreparedStmts 表示是否使用预编译功能 通过PreparedStatement的子类ServerPreparedStatement来实现PreparedStaetment的预编译功能
			//`com.mysql.jdbc.ServerPreparedStatement`中的`serverExecute`方法负责告诉server使用当前提供的参数来动态绑定到编译好的sql语句上
			if (this.useServerPreparedStmts && canServerPrepare) {//使用预编译功能
				if (this.getCachePreparedStatements()) {
					synchronized (this.serverSideStatementCache) {
						pStmt = (com.mysql.jdbc.ServerPreparedStatement)this.serverSideStatementCache.remove(sql);
						if (pStmt != null) {
							((com.mysql.jdbc.ServerPreparedStatement)pStmt).setClosed(false);
							pStmt.clearParameters();
						}
						if (pStmt == null) {
							try {
								pStmt = ServerPreparedStatement.getInstance(getLoadBalanceSafeProxy(), nativeSql,
										this.database, resultSetType, resultSetConcurrency);
								if (sql.length() < getPreparedStatementCacheSqlLimit()) {
									((com.mysql.jdbc.ServerPreparedStatement)pStmt).isCached = true;
								}
								pStmt.setResultSetType(resultSetType);
								pStmt.setResultSetConcurrency(resultSetConcurrency);
							} catch (SQLException sqlEx) {
								// Punt, if necessary
								if (getEmulateUnsupportedPstmts()) {
									pStmt = (PreparedStatement) clientPrepareStatement(nativeSql, resultSetType, resultSetConcurrency, false);
									if (sql.length() < getPreparedStatementCacheSqlLimit()) {
										this.serverSideStatementCheckCache.put(sql, Boolean.FALSE);
									}
								} else {
									throw sqlEx;
								}
							}
						}
					}
				} else {
					try {
						pStmt = ServerPreparedStatement.getInstance(getLoadBalanceSafeProxy(), nativeSql,
								this.database, resultSetType, resultSetConcurrency);
						pStmt.setResultSetType(resultSetType);
						pStmt.setResultSetConcurrency(resultSetConcurrency);
					} catch (SQLException sqlEx) {
						// Punt, if necessary
						if (getEmulateUnsupportedPstmts()) {
							pStmt = (PreparedStatement) clientPrepareStatement(nativeSql, resultSetType, resultSetConcurrency, false);
						} else {
							throw sqlEx;
						}
					}
				}
			} else {
				pStmt = (PreparedStatement) clientPrepareStatement(nativeSql, resultSetType, resultSetConcurrency, false);
			}
			return pStmt;
		}
	}
	/**
	 * @see Connection#prepareStatement(String, int, int, int)
	 */
	public java.sql.PreparedStatement prepareStatement(String sql,
			int resultSetType, int resultSetConcurrency,
			int resultSetHoldability) throws SQLException {
		if (getPedantic()) {
			if (resultSetHoldability != java.sql.ResultSet.HOLD_CURSORS_OVER_COMMIT) {
				throw SQLError.createSQLException(
						"HOLD_CUSRORS_OVER_COMMIT is only supported holdability level",
						SQLError.SQL_STATE_ILLEGAL_ARGUMENT, getExceptionInterceptor());
			}
		}
		return prepareStatement(sql, resultSetType, resultSetConcurrency);
	}
	/**
	 * @see Connection#prepareStatement(String, int[])
	 */
	public java.sql.PreparedStatement prepareStatement(String sql,
			int[] autoGenKeyIndexes) throws SQLException {
		java.sql.PreparedStatement pStmt = prepareStatement(sql);
		((com.mysql.jdbc.PreparedStatement) pStmt)
				.setRetrieveGeneratedKeys((autoGenKeyIndexes != null)
						&& (autoGenKeyIndexes.length > 0));
		return pStmt;
	}
	/**
	 * @see Connection#prepareStatement(String, String[])
	 */
	public java.sql.PreparedStatement prepareStatement(String sql,
			String[] autoGenKeyColNames) throws SQLException {
		java.sql.PreparedStatement pStmt = prepareStatement(sql);
		((com.mysql.jdbc.PreparedStatement) pStmt)
				.setRetrieveGeneratedKeys((autoGenKeyColNames != null)
						&& (autoGenKeyColNames.length > 0));
		return pStmt;
	}
	
```
ResultSet中常用的参数常量主要有:
```
    //按正向处理结果集中的行(从第一个到最后一个) 可以通过方法setFetchDirection来设置
    int FETCH_FORWARD = 1000;
    //按逆向处理结果集中的行(从最后一个到第一个)可以通过方法setFetchDirection来设置
    int FETCH_REVERSE = 1001;
    //结果集中的行处理顺序未知 可以通过方法setFetchDirection来设置
    int FETCH_UNKNOWN = 1002;
    //ResultSet的游标只能向前移动
	int TYPE_FORWARD_ONLY = 1003;
    //ResultSet的游标可以滚动 但是对于ResultSet中的数据变化不敏感
    int TYPE_SCROLL_INSENSITIVE = 1004;
	//ResultSet的游标可以滚动 对于ResultSet中的数据变化敏感
    int TYPE_SCROLL_SENSITIVE = 1005;
    //不可以更新的ResultSet的并发模式
    int CONCUR_READ_ONLY = 1007;
	//可以更新的ResultSet的并发模式
    int CONCUR_UPDATABLE = 1008;
    //当调用connection.commit时保持ResultSet不被关闭
    int HOLD_CURSORS_OVER_COMMIT = 1;
	//当调用connection.commit时应关闭ResultSet
    int CLOSE_CURSORS_AT_COMMIT = 2;
    
```
此时PreparedStatement对象已经建立,我们可以通过PreparedStatement.executeQuery()方法来执行sql并返回ResultSet

```
public java.sql.ResultSet executeQuery() throws SQLException {
		synchronized (checkClosed().getConnectionMutex()) {
			MySQLConnection locallyScopedConn = this.connection;
			//检查this.originalSql是否是INSERT、UPDATE、DELETE、DROP、CREATE、ALTER、TRUNCATE、RENAME的 DML
			//如果是就抛出SQLError.SQL_STATE_ILLEGAL_ARGUMENT异常
			checkForDml(this.originalSql, this.firstCharOfStmt);
			CachedResultSetMetaData cachedMetadata = null;
			clearWarnings();
			boolean doStreaming = createStreamingResultSet();
			this.batchedGeneratedKeys = null;
			// Adjust net_write_timeout to a higher value if we're
			// streaming result sets. More often than not, someone runs into
			// an issue where they blow net_write_timeout when using this
			// feature, and if they're willing to hold a result set open
			// for 30 seconds or more, one more round-trip isn't going to hurt
			//
			// This is reset by RowDataDynamic.close().
			if (doStreaming
					&& this.connection.getNetTimeoutForStreamingResults() > 0) {
				java.sql.Statement stmt = null;
				try {
					//创建Statement对象
					stmt = this.connection.createStatement();
					((com.mysql.jdbc.StatementImpl)stmt).executeSimpleNonQuery(this.connection, "SET net_write_timeout=" 
							+ this.connection.getNetTimeoutForStreamingResults());
				} finally {
					if (stmt != null) {
						stmt.close();
					}
				}
			}
			Buffer sendPacket = fillSendPacket();//创建数据包,其中包含了要发送给Mysql Server的查询信息
			//关闭Statement中所有的resultSet
			implicitlyCloseAllOpenResults();
			String oldCatalog = null;
			//设置当前的数据库名 并将之前的数据库名记录下来 查询完成之后还有恢复过来
			if (!locallyScopedConn.getCatalog().equals(this.currentCatalog)) {
				oldCatalog = locallyScopedConn.getCatalog();
				locallyScopedConn.setCatalog(this.currentCatalog);
			}
			//
			// Check if we have cached metadata for this query...
			//
			//检查是否有缓存的数据 如果有直接 从缓存中取
			if (locallyScopedConn.getCacheResultSetMetadata()) {
				cachedMetadata = locallyScopedConn.getCachedMetaData(this.originalSql);
			}
			Field[] metadataFromCache = null;
			if (cachedMetadata != null) {
				metadataFromCache = cachedMetadata.fields;
			}
			if (locallyScopedConn.useMaxRows()) {
				// If there isn't a limit clause in the SQL
				// then limit the number of rows to return in
				// an efficient manner. Only do this if
				// setMaxRows() hasn't been used on any Statements
				// generated from the current Connection (saves
				// a query, and network traffic).
				if (this.hasLimitClause) {//当前SQL中是否包含limit语句
					//executeInternal执行查询
					this.results = executeInternal(this.maxRows, sendPacket,
							createStreamingResultSet(), true,
							metadataFromCache, false);
				} else {
					if (this.maxRows <= 0) {
						executeSimpleNonQuery(locallyScopedConn,
								"SET SQL_SELECT_LIMIT=DEFAULT");
					} else {
						executeSimpleNonQuery(locallyScopedConn,
								"SET SQL_SELECT_LIMIT=" + this.maxRows);
					}
					this.results = executeInternal(-1, sendPacket,
							doStreaming, true,
							metadataFromCache, false);
					if (oldCatalog != null) {
						this.connection.setCatalog(oldCatalog);
					}
				}
			} else {
				this.results = executeInternal(-1, sendPacket,
						doStreaming, true,
						metadataFromCache, false);
			}
			if (oldCatalog != null) {
				locallyScopedConn.setCatalog(oldCatalog);
			}
			if (cachedMetadata != null) {
				locallyScopedConn.initializeResultsMetadataFromCache(this.originalSql,
						cachedMetadata, this.results);
			} else {
				if (locallyScopedConn.getCacheResultSetMetadata()) {
					locallyScopedConn.initializeResultsMetadataFromCache(this.originalSql,
							null /* will be created */, this.results);
				}
			}
			this.lastInsertId = this.results.getUpdateID();
			return this.results;
		}
	}
```
在执行查询之前我们需要封装发送给Mysql server的sendPacket包

```
/**
	 * 封装要发送给Mysql server的sendPacket包
	 * @param batchedParameterStrings 参数转换成byte后的值(statement.set(1,"1")来设置sql语句参数的值)
	 * @param batchedParameterStreams 只有在调用存储过程batch(CallableStatement)的时候才会用到它,否则它数组中的值设置为null
	 * @param batchedIsStream 是否为stream的标志，若调用的是PreparedStatement数组中的值均为false,若调用的是CallableStatement则数组中的值均为true
	 * @param batchedStreamLengths 标识参数是否为null 设置为false
	 * 这个几个一维数组大小一致 有几个待设置的参数  一维数组的大小就是多少
	 * @return
	 * @throws SQLException
	 */
	protected Buffer fillSendPacket(byte'[']''[']' batchedParameterStrings,
			InputStream[] batchedParameterStreams, boolean[] batchedIsStream,
			int[] batchedStreamLengths) throws SQLException {
		synchronized (checkClosed().getConnectionMutex()) {
			//从connection的io中获取发送数据包 然后清空它
			Buffer sendPacket = this.connection.getIO().getSharedSendPacket();
			//首先清空这个数据包
			sendPacket.clear();
			//数据包的第一位为一个操作标识符(MysqlDefs.QUERY)表示驱动向服务器发送的连接的操作信号,
			//包括QUERY,PING,RELOAD,SHUTDOWN,PROCESS_INFO,QUIT,SLEEP等等
			//该操作信号并不是针对sql语句的CRUD操作 而是针对服务器的一个操作
			sendPacket.writeByte((byte) MysqlDefs.QUERY);
			boolean useStreamLengths = this.connection
					.getUseStreamLengthsInPrepStmts();
			//
			// Try and get this allocation as close as possible
			// for BLOBs
			//
			int ensurePacketSize = 0;
			String statementComment = this.connection.getStatementComment();
			byte[] commentAsBytes = null;
			if (statementComment != null) {
				if (this.charConverter != null) {
					commentAsBytes = this.charConverter.toBytes(statementComment);
				} else {
					commentAsBytes = StringUtils.getBytes(statementComment, this.charConverter,
							this.charEncoding, this.connection
									.getServerCharacterEncoding(), this.connection
									.parserKnowsUnicode(), getExceptionInterceptor());
				}
				ensurePacketSize += commentAsBytes.length;
				ensurePacketSize += 6; // for /*[space] [space]*/
			}
			for (int i = 0; i < batchedParameterStrings.length; i++) {
				if (batchedIsStream[i] && useStreamLengths) {
					ensurePacketSize += batchedStreamLengths[i];
				}
			}
			//判断sendPacket是否够大 否则按照1.25倍来扩充
			if (ensurePacketSize != 0) {
				sendPacket.ensureCapacity(ensurePacketSize);
			}
			if (commentAsBytes != null) {
				sendPacket.writeBytesNoNull(Constants.SLASH_STAR_SPACE_AS_BYTES);
				sendPacket.writeBytesNoNull(commentAsBytes);
				sendPacket.writeBytesNoNull(Constants.SPACE_STAR_SLASH_SPACE_AS_BYTES);
			}
			//遍历所有参数 将之前分割的sql(根据sql中的?将sql分成字符串数组staticSqlStrings) 与现在的参数列表合并  拼装出sql
			for (int i = 0; i < batchedParameterStrings.length; i++) {
				//batchedParameterStrings 和batchedParameterStreams均为空 则抛出异常 表示参数设置出错
				checkAllParametersSet(batchedParameterStrings[i],
						batchedParameterStreams[i], i);
				//将之前分割的sql转成byte[]写入到sendPacket
				sendPacket.writeBytesNoNull(this.staticSqlStrings[i]);
				//如果参数是通过CallableStatement传递过来的 就使用batchedParameterStreams中的值来替换sql中的?号占位
				if (batchedIsStream[i]) {
					streamToBytes(sendPacket, batchedParameterStreams[i], true,
							batchedStreamLengths[i], useStreamLengths);
				} else {
					//否则就用batchedParameterStrings来替换sql中的?占位
					sendPacket.writeBytesNoNull(batchedParameterStrings[i]);
				}
			}
			//原始sql中的最后一个'?'后面可能还会有order by等语句,因此staticSqlStrings的长度会比参数的个数大1
			//这里将staticSqlStrings中最后一段sql加入到sendPacket中
			sendPacket
					.writeBytesNoNull(this.staticSqlStrings[batchedParameterStrings.length]);
			return sendPacket;
		}
	}
```

封装玩数据包后,调用PreparedStatement.executeInternal执行SQL

```
protected ResultSetInternalMethods executeInternal(int maxRowsToRetrieve,
			Buffer sendPacket, boolean createStreamingResultSet,
			boolean queryIsSelectOnly, Field[] metadataFromCache,
			boolean isBatch)
			throws SQLException {
		synchronized (checkClosed().getConnectionMutex()) {
			try {
				resetCancelledState();
				//设置当前连接
				MySQLConnection locallyScopedConnection = this.connection;
				this.numberOfExecutions++;
				if (this.doPingInstead) {
					doPingInstead();
					return this.results;
				}
				ResultSetInternalMethods rs;
				CancelTask timeoutTask = null;
				try {
					if (locallyScopedConnection.getEnableQueryTimeouts() &&
							this.timeoutInMillis != 0
							&& locallyScopedConnection.versionMeetsMinimum(5, 0, 0)) {
						timeoutTask = new CancelTask(this);
						locallyScopedConnection.getCancelTimer().schedule(timeoutTask, 
								this.timeoutInMillis);
					}
					if (!isBatch) {
						statementBegins();
					}
					//调用当前连接执行execSQL 然后将之前组装好的sendPacket传递给MysqlIO的sqlQueryDirect()
					rs = locallyScopedConnection.execSQL(this, null, maxRowsToRetrieve, sendPacket,
						this.resultSetType, this.resultSetConcurrency,
						createStreamingResultSet, this.currentCatalog,
						metadataFromCache, isBatch);
					if (timeoutTask != null) {
						timeoutTask.cancel();
						locallyScopedConnection.getCancelTimer().purge();
						if (timeoutTask.caughtWhileCancelling != null) {
							throw timeoutTask.caughtWhileCancelling;
						}
						timeoutTask = null;
					}
					synchronized (this.cancelTimeoutMutex) {
						if (this.wasCancelled) {
							SQLException cause = null;
							if (this.wasCancelledByTimeout) {
								cause = new MySQLTimeoutException();
							} else {
								cause = new MySQLStatementCancelledException();
							}
							resetCancelledState();
							throw cause;
						}
					}
				} finally {
					if (!isBatch) {
						this.statementExecuting.set(false);
					}
					if (timeoutTask != null) {
						timeoutTask.cancel();
						locallyScopedConnection.getCancelTimer().purge();
					}
				}
				return rs;
			} catch (NullPointerException npe) {
				checkClosed(); // we can't synchronize ourselves against async connection-close
				               // due to deadlock issues, so this is the next best thing for
				 			   // this particular corner case.
				throw npe;
			}
		}
	}
```

将当前的连接设置为ConnectionImpl,并调用其中的`execSQL`方法 将sendPacket数据包交给MySQLIO的sqlQueryDirect()

```
public ResultSetInternalMethods execSQL(StatementImpl callingStatement, String sql, int maxRows,
			Buffer packet, int resultSetType, int resultSetConcurrency,
			boolean streamResults, String catalog,
			Field[] cachedMetadata,
			boolean isBatch) throws SQLException {
		synchronized (getConnectionMutex()) {
			//
			// Fall-back if the master is back online if we've
			// issued queriesBeforeRetryMaster queries since
			// we failed over
			//
			long queryStartTime = 0;
			int endOfQueryPacketPosition = 0;
			if (packet != null) {
				endOfQueryPacketPosition = packet.getPosition();
			}
			if (getGatherPerformanceMetrics()) {
				queryStartTime = System.currentTimeMillis();
			}
			this.lastQueryFinishedTime = 0; // we're busy!
			if ((getHighAvailability())
					&& (this.autoCommit || getAutoReconnectForPools())
					&& this.needsPing && !isBatch) {
				try {
					pingInternal(false, 0);
					this.needsPing = false;
				} catch (Exception Ex) {
					createNewIO(true);
				}
			}
			try {
				if (packet == null) {
					String encoding = null;
					if (getUseUnicode()) {
						encoding = getEncoding();
					}
					//通过MySQLIO向Mysql server发送命令
					return this.io.sqlQueryDirect(callingStatement, sql,
							encoding, null, maxRows, resultSetType,
							resultSetConcurrency, streamResults, catalog,
							cachedMetadata);
				}
				return this.io.sqlQueryDirect(callingStatement, null, null,
						packet, maxRows, resultSetType,
						resultSetConcurrency, streamResults, catalog,
						cachedMetadata);
			} catch (java.sql.SQLException sqlE) {
				// don't clobber SQL exceptions
				if (getDumpQueriesOnException()) {
					String extractedSql = extractSqlFromPacket(sql, packet,
							endOfQueryPacketPosition);
					StringBuffer messageBuf = new StringBuffer(extractedSql
							.length() + 32);
					messageBuf
							.append("\n\nQuery being executed when exception was thrown:\n");
					messageBuf.append(extractedSql);
					messageBuf.append("\n\n");
					sqlE = appendMessageToException(sqlE, messageBuf.toString(), getExceptionInterceptor());
				}
				if ((getHighAvailability())) {
					this.needsPing = true;
				} else {
					String sqlState = sqlE.getSQLState();
					if ((sqlState != null)
							&& sqlState
									.equals(SQLError.SQL_STATE_COMMUNICATION_LINK_FAILURE)) {
						cleanup(sqlE);
					}
				}
				throw sqlE;
			} catch (Exception ex) {
				if (getHighAvailability()) {
					this.needsPing = true;
				} else if (ex instanceof IOException) {
					cleanup(ex);
				}
				SQLException sqlEx = SQLError.createSQLException(
						Messages.getString("Connection.UnexpectedException"),
						SQLError.SQL_STATE_GENERAL_ERROR, getExceptionInterceptor());
				sqlEx.initCause(ex);
				throw sqlEx;
			} finally {
				if (getMaintainTimeStats()) {
					this.lastQueryFinishedTime = System.currentTimeMillis();
				}
				if (getGatherPerformanceMetrics()) {
					long queryTime = System.currentTimeMillis()
							- queryStartTime;
					registerQueryExecutionTime(queryTime);
				}
			}
		}
	}
```

####MysqlIO.sqlQueryDirect
-------------------------
```
 final ResultSetInternalMethods sqlQueryDirect(StatementImpl callingStatement, String query,
    		String characterEncoding, Buffer queryPacket, int maxRows,
    		int resultSetType, int resultSetConcurrency,
    		boolean streamResults, String catalog, Field[] cachedMetadata)
    throws Exception {
    	this.statementExecutionDepth++;

    	try {
	    	if (this.statementInterceptors != null) {
	    		ResultSetInternalMethods interceptedResults =
	    			invokeStatementInterceptorsPre(query, callingStatement, false);

	    		if (interceptedResults != null) {
	    			return interceptedResults;
	    		}
	    	}

	    	long queryStartTime = 0;
	    	long queryEndTime = 0;

    		String statementComment = this.connection.getStatementComment();
    		
    		if (this.connection.getIncludeThreadNamesAsStatementComment()) {
    			statementComment = (statementComment != null ? statementComment + ", " : "") + "java thread: " + Thread.currentThread().getName();
    		}
    		
	    	if (query != null) {
	    		// We don't know exactly how many bytes we're going to get
	    		// from the query. Since we're dealing with Unicode, the
	    		// max is 2, so pad it (2 * query) + space for headers
	    		int packLength = HEADER_LENGTH + 1 + (query.length() * 3) + 2;

	    		byte[] commentAsBytes = null;

	    		if (statementComment != null) {
	    			commentAsBytes = StringUtils.getBytes(statementComment, null,
	    					characterEncoding, this.connection
	    					.getServerCharacterEncoding(),
	    					this.connection.parserKnowsUnicode(), getExceptionInterceptor());

	    			packLength += commentAsBytes.length;
	    			packLength += 6; // for /*[space] [space]*/
	    		}

	    		if (this.sendPacket == null) {//将sendPacket封装的数据包 发送给服务器
	    			this.sendPacket = new Buffer(packLength);
	    		} else {
	    			this.sendPacket.clear();
	    		}

	    		this.sendPacket.writeByte((byte) MysqlDefs.QUERY);

	    		if (commentAsBytes != null) {
	    			this.sendPacket.writeBytesNoNull(Constants.SLASH_STAR_SPACE_AS_BYTES);
	    			this.sendPacket.writeBytesNoNull(commentAsBytes);
	    			this.sendPacket.writeBytesNoNull(Constants.SPACE_STAR_SLASH_SPACE_AS_BYTES);
	    		}

	    		if (characterEncoding != null) {
	    			if (this.platformDbCharsetMatches) {
	    				this.sendPacket.writeStringNoNull(query, characterEncoding,
	    						this.connection.getServerCharacterEncoding(),
	    						this.connection.parserKnowsUnicode(),
	    						this.connection);
	    			} else {
	    				if (StringUtils.startsWithIgnoreCaseAndWs(query, "LOAD DATA")) { //$NON-NLS-1$
	    					this.sendPacket.writeBytesNoNull(StringUtils.getBytes(query));
	    				} else {
	    					this.sendPacket.writeStringNoNull(query,
	    							characterEncoding,
	    							this.connection.getServerCharacterEncoding(),
	    							this.connection.parserKnowsUnicode(),
	    							this.connection);
	    				}
	    			}
	    		} else {
	    			this.sendPacket.writeStringNoNull(query);
	    		}

	    		queryPacket = this.sendPacket;//数据包赋值
	    	}

	    	byte[] queryBuf = null;
	    	int oldPacketPosition = 0;

	    	if (needToGrabQueryFromPacket) {
	    		queryBuf = queryPacket.getByteBuffer();

	    		// save the packet position
	    		oldPacketPosition = queryPacket.getPosition();

	    		queryStartTime = getCurrentTimeNanosOrMillis();
	    	}
	    	
	    	if (this.autoGenerateTestcaseScript) {
	    		String testcaseQuery = null;

	    		if (query != null) {
	    			if (statementComment != null) {
	    				testcaseQuery = "/* " + statementComment + " */ " + query;
	    			} else {
	    				testcaseQuery = query;
	    			}
	    		} else {
	    			testcaseQuery = StringUtils.toString(queryBuf, 5,
	    					(oldPacketPosition - 5));
	    		}

	    		StringBuffer debugBuf = new StringBuffer(testcaseQuery.length() + 32);
	    		this.connection.generateConnectionCommentBlock(debugBuf);
	    		debugBuf.append(testcaseQuery);
	    		debugBuf.append(';');
	    		this.connection.dumpTestcaseQuery(debugBuf.toString());
	    	}

	    	// Send query command and sql query string
	    	//发送查询命令与sql查询语句 并得到查询结果(socket处理)
	    	Buffer resultPacket = sendCommand(MysqlDefs.QUERY, null, queryPacket,
	    			false, null, 0);

	    	long fetchBeginTime = 0;
	    	long fetchEndTime = 0;

	    	String profileQueryToLog = null;

	    	boolean queryWasSlow = false;

	    	if (this.profileSql || this.logSlowQueries) {
	    		queryEndTime = getCurrentTimeNanosOrMillis();

	    		boolean shouldExtractQuery = false;

	    		if (this.profileSql) {
	    			shouldExtractQuery = true;
	    		} else if (this.logSlowQueries) {
	    			long queryTime = queryEndTime - queryStartTime;
	    			
	    			boolean logSlow = false;
	    			
	    			if (!this.useAutoSlowLog) {
	    				logSlow = queryTime > this.connection.getSlowQueryThresholdMillis();
	    			} else {
	    				logSlow = this.connection.isAbonormallyLongQuery(queryTime);
	    				
	    				this.connection.reportQueryTime(queryTime);
	    			}
	    			
	    			if (logSlow) {
	    				shouldExtractQuery = true;
	    				queryWasSlow = true;
	    			}
	    		}

	    		if (shouldExtractQuery) {
	    			// Extract the actual query from the network packet
	    			boolean truncated = false;

	    			int extractPosition = oldPacketPosition;

	    			if (oldPacketPosition > this.connection.getMaxQuerySizeToLog()) {
	    				extractPosition = this.connection.getMaxQuerySizeToLog() + 5;
	    				truncated = true;
	    			}

	    			profileQueryToLog = StringUtils.toString(queryBuf, 5,
	    					(extractPosition - 5));

	    			if (truncated) {
	    				profileQueryToLog += Messages.getString("MysqlIO.25"); //$NON-NLS-1$
	    			}
	    		}

	    		fetchBeginTime = queryEndTime;
	    	}
	    	//封装成ResultSet
	    	ResultSetInternalMethods rs = readAllResults(callingStatement, maxRows, resultSetType,
	    			resultSetConcurrency, streamResults, catalog, resultPacket,
	    			false, -1L, cachedMetadata);

	    	if (queryWasSlow && !this.serverQueryWasSlow /* don't log slow queries twice */) {
	    		StringBuffer mesgBuf = new StringBuffer(48 +
	    				profileQueryToLog.length());

	    		mesgBuf.append(Messages.getString("MysqlIO.SlowQuery",
	    				new Object[] {String.valueOf(this.useAutoSlowLog ? 
	    						" 95% of all queries " : this.slowQueryThreshold),
	    				queryTimingUnits,
	    				Long.valueOf(queryEndTime - queryStartTime)}));
	    		mesgBuf.append(profileQueryToLog);

	    		ProfilerEventHandler eventSink = ProfilerEventHandlerFactory.getInstance(this.connection);

	    		eventSink.consumeEvent(new ProfilerEvent(ProfilerEvent.TYPE_SLOW_QUERY,
	    				"", catalog, this.connection.getId(), //$NON-NLS-1$
	    				(callingStatement != null) ? callingStatement.getId() : 999,
	    						((ResultSetImpl)rs).resultId, System.currentTimeMillis(),
	    						(int) (queryEndTime - queryStartTime), queryTimingUnits, null,
	    						LogUtils.findCallingClassAndMethod(new Throwable()), mesgBuf.toString()));

	    		if (this.connection.getExplainSlowQueries()) {
	    			if (oldPacketPosition < MAX_QUERY_SIZE_TO_EXPLAIN) {
	    				explainSlowQuery(queryPacket.getBytes(5,
	    						(oldPacketPosition - 5)), profileQueryToLog);
	    			} else {
	    				this.connection.getLog().logWarn(Messages.getString(
	    						"MysqlIO.28") //$NON-NLS-1$
	    						+MAX_QUERY_SIZE_TO_EXPLAIN +
	    						Messages.getString("MysqlIO.29")); //$NON-NLS-1$
	    			}
	    		}
	    	}

	    	if (this.logSlowQueries) {

	    		ProfilerEventHandler eventSink = ProfilerEventHandlerFactory.getInstance(this.connection);

	    		if (this.queryBadIndexUsed && this.profileSql) {
	    			eventSink.consumeEvent(new ProfilerEvent(
	    					ProfilerEvent.TYPE_SLOW_QUERY, "", catalog, //$NON-NLS-1$
	    					this.connection.getId(),
	    					(callingStatement != null) ? callingStatement.getId()
	    							: 999, ((ResultSetImpl)rs).resultId,
	    							System.currentTimeMillis(),
	    							(queryEndTime - queryStartTime), this.queryTimingUnits,
	    							null,
	    							LogUtils.findCallingClassAndMethod(new Throwable()),
	    							Messages.getString("MysqlIO.33") //$NON-NLS-1$
	    							+profileQueryToLog));
	    		}

	    		if (this.queryNoIndexUsed && this.profileSql) {
	    			eventSink.consumeEvent(new ProfilerEvent(
	    					ProfilerEvent.TYPE_SLOW_QUERY, "", catalog, //$NON-NLS-1$
	    					this.connection.getId(),
	    					(callingStatement != null) ? callingStatement.getId()
	    							: 999, ((ResultSetImpl)rs).resultId,
	    							System.currentTimeMillis(),
	    							(queryEndTime - queryStartTime), this.queryTimingUnits,
	    							null,
	    							LogUtils.findCallingClassAndMethod(new Throwable()),
	    							Messages.getString("MysqlIO.35") //$NON-NLS-1$
	    							+profileQueryToLog));
	    		}
	    		
	    		if (this.serverQueryWasSlow && this.profileSql) {
	    			eventSink.consumeEvent(new ProfilerEvent(
	    					ProfilerEvent.TYPE_SLOW_QUERY, "", catalog, //$NON-NLS-1$
	    					this.connection.getId(),
	    					(callingStatement != null) ? callingStatement.getId()
	    							: 999, ((ResultSetImpl)rs).resultId,
	    							System.currentTimeMillis(),
	    							(queryEndTime - queryStartTime), this.queryTimingUnits,
	    							null,
	    							LogUtils.findCallingClassAndMethod(new Throwable()),
	    							Messages.getString("MysqlIO.ServerSlowQuery") //$NON-NLS-1$
	    							+profileQueryToLog));
	    		}
	    	}

	    	if (this.profileSql) {
	    		fetchEndTime = getCurrentTimeNanosOrMillis();

	    		ProfilerEventHandler eventSink = ProfilerEventHandlerFactory.getInstance(this.connection);

	    		eventSink.consumeEvent(new ProfilerEvent(ProfilerEvent.TYPE_QUERY,
	    				"", catalog, this.connection.getId(), //$NON-NLS-1$
	    				(callingStatement != null) ? callingStatement.getId() : 999,
	    						((ResultSetImpl)rs).resultId, System.currentTimeMillis(),
	    						(queryEndTime - queryStartTime), this.queryTimingUnits,
	    						null,
	    						LogUtils.findCallingClassAndMethod(new Throwable()), profileQueryToLog));

	    		eventSink.consumeEvent(new ProfilerEvent(ProfilerEvent.TYPE_FETCH,
	    				"", catalog, this.connection.getId(), //$NON-NLS-1$
	    				(callingStatement != null) ? callingStatement.getId() : 999,
	    						((ResultSetImpl)rs).resultId, System.currentTimeMillis(),
	    						(fetchEndTime - fetchBeginTime), this.queryTimingUnits,
	    						null,
	    						LogUtils.findCallingClassAndMethod(new Throwable()), null));
	    	}

	    	if (this.hadWarnings) {
	    		scanForAndThrowDataTruncation();
	    	}

	    	if (this.statementInterceptors != null) {
	    		ResultSetInternalMethods interceptedResults = invokeStatementInterceptorsPost(
	    				query, callingStatement, rs, false, null);

	    		if (interceptedResults != null) {
	    			rs = interceptedResults;
	    		}
	    	}

	    	return rs;
    	} catch (SQLException sqlEx) {
    		if (this.statementInterceptors != null) {
	    		invokeStatementInterceptorsPost(
	    				query, callingStatement, null, false, sqlEx); // we don't do anything with the result set in this case
    		}
    		
    		if (callingStatement != null) {
    			synchronized (callingStatement.cancelTimeoutMutex) {
	    			if (callingStatement.wasCancelled) {
						SQLException cause = null;
						
						if (callingStatement.wasCancelledByTimeout) {
							cause = new MySQLTimeoutException();
						} else {
							cause = new MySQLStatementCancelledException();
						}
						
						callingStatement.resetCancelledState();
						
						throw cause;
					}
    			}
    		}
    		
    		throw sqlEx;
    	} finally {
    		this.statementExecutionDepth--;
    	}
    }
```

####MysqlIO.readAllResults 封装ResulSet
--------------------------------------

```
ResultSetImpl readAllResults(StatementImpl callingStatement, int maxRows,
        int resultSetType, int resultSetConcurrency, boolean streamResults,
        String catalog, Buffer resultPacket, boolean isBinaryEncoded,
        long preSentColumnCount, Field[] metadataFromCache)
        throws SQLException {
    	//设置指针
        resultPacket.setPosition(resultPacket.getPosition() - 1);
        //读取第一条数据
        ResultSetImpl topLevelResultSet = readResultsForQueryOrUpdate(callingStatement,
                maxRows, resultSetType, resultSetConcurrency, streamResults,
                catalog, resultPacket, isBinaryEncoded, preSentColumnCount,
                metadataFromCache);

        ResultSetImpl currentResultSet = topLevelResultSet;

        boolean checkForMoreResults = ((this.clientParam &
            CLIENT_MULTI_RESULTS) != 0);

        boolean serverHasMoreResults = (this.serverStatus &
            SERVER_MORE_RESULTS_EXISTS) != 0;

        //
        // TODO: We need to support streaming of multiple result sets
        //
        if (serverHasMoreResults && streamResults) {
            //clearInputStream();
//
            //throw SQLError.createSQLException(Messages.getString("MysqlIO.23"), //$NON-NLS-1$
                //SQLError.SQL_STATE_DRIVER_NOT_CAPABLE);
        	if (topLevelResultSet.getUpdateCount() != -1) {
        		tackOnMoreStreamingResults(topLevelResultSet);
        	}
        	
        	reclaimLargeReusablePacket();
        	
        	return topLevelResultSet;
        }

        boolean moreRowSetsExist = checkForMoreResults & serverHasMoreResults;

        while (moreRowSetsExist) {
        	Buffer fieldPacket = checkErrorPacket();
            fieldPacket.setPosition(0);

            ResultSetImpl newResultSet = readResultsForQueryOrUpdate(callingStatement,
                    maxRows, resultSetType, resultSetConcurrency,
                    streamResults, catalog, fieldPacket, isBinaryEncoded,
                    preSentColumnCount, metadataFromCache);

            currentResultSet.setNextResultSet(newResultSet);

            currentResultSet = newResultSet;

            moreRowSetsExist = (this.serverStatus & SERVER_MORE_RESULTS_EXISTS) != 0;
        }

        if (!streamResults) {
            clearInputStream();
        }

        reclaimLargeReusablePacket();

        return topLevelResultSet;
    }
```

MysqlIO.readResultsForQueryOrUpdate

```
protected final ResultSetImpl readResultsForQueryOrUpdate(
        StatementImpl callingStatement, int maxRows, int resultSetType,
        int resultSetConcurrency, boolean streamResults, String catalog,
        Buffer resultPacket, boolean isBinaryEncoded, long preSentColumnCount,
        Field[] metadataFromCache) throws SQLException {
        long columnCount = resultPacket.readFieldLength();

        if (columnCount == 0) {
            return buildResultSetWithUpdates(callingStatement, resultPacket);
        } else if (columnCount == Buffer.NULL_LENGTH) {
            String charEncoding = null;

            if (this.connection.getUseUnicode()) {
                charEncoding = this.connection.getEncoding();
            }

            String fileName = null;

            if (this.platformDbCharsetMatches) {
                fileName = ((charEncoding != null)
                    ? resultPacket.readString(charEncoding, getExceptionInterceptor())
                    : resultPacket.readString());
            } else {
                fileName = resultPacket.readString();
            }

            return sendFileToServer(callingStatement, fileName);
        } else {
        //获取结果集
            com.mysql.jdbc.ResultSetImpl results = getResultSet(callingStatement,
                    columnCount, maxRows, resultSetType, resultSetConcurrency,
                    streamResults, catalog, isBinaryEncoded,
                    metadataFromCache);

            return results;
        }
    }
```

MysqlIO.getResultSet

```
//对数据进行解析封装成结果集
    protected ResultSetImpl getResultSet(StatementImpl callingStatement,
        long columnCount, int maxRows, int resultSetType,
        int resultSetConcurrency, boolean streamResults, String catalog,
        boolean isBinaryEncoded, Field[] metadataFromCache)
        throws SQLException {
        Buffer packet; // The packet from the server
        Field[] fields = null;//字段数组

        // Read in the column information

        if (metadataFromCache == null /* we want the metadata from the server */) {
            fields = new Field[(int) columnCount];

            for (int i = 0; i < columnCount; i++) {
            	Buffer fieldPacket = null;

                fieldPacket = readPacket();//循环处理
                fields[i] = unpackField(fieldPacket, false);
            }
        } else {
        	for (int i = 0; i < columnCount; i++) {
        		skipPacket();
        	}
        }

        packet = reuseAndReadPacket(this.reusablePacket);
        
        readServerStatusForResultSets(packet);

		//
		// Handle cursor-based fetch first
		//

		if (this.connection.versionMeetsMinimum(5, 0, 2)
				&& this.connection.getUseCursorFetch()
				&& isBinaryEncoded
				&& callingStatement != null
				&& callingStatement.getFetchSize() != 0
				&& callingStatement.getResultSetType() == ResultSet.TYPE_FORWARD_ONLY) {
			ServerPreparedStatement prepStmt = (com.mysql.jdbc.ServerPreparedStatement) callingStatement;

			boolean usingCursor = true;

			//
			// Server versions 5.0.5 or newer will only open
			// a cursor and set this flag if they can, otherwise
			// they punt and go back to mysql_store_results() behavior
			//

			if (this.connection.versionMeetsMinimum(5, 0, 5)) {
				usingCursor = (this.serverStatus &
						SERVER_STATUS_CURSOR_EXISTS) != 0;
			}

			if (usingCursor) {
				RowData rows = new RowDataCursor(
					this,
					prepStmt,
					fields);

				ResultSetImpl rs = buildResultSetWithRows(
					callingStatement,
					catalog,
					fields,
					rows, resultSetType, resultSetConcurrency, isBinaryEncoded);

				if (usingCursor) {
		        	rs.setFetchSize(callingStatement.getFetchSize());
		        }

				return rs;
			}
		}

        RowData rowData = null;

        if (!streamResults) {
        	//封装成rowData的数据
            rowData = readSingleRowSet(columnCount, maxRows,
                    resultSetConcurrency, isBinaryEncoded,
                    (metadataFromCache == null) ? fields : metadataFromCache);
        } else {
            rowData = new RowDataDynamic(this, (int) columnCount,
            		(metadataFromCache == null) ? fields : metadataFromCache,
                    isBinaryEncoded);
            this.streamingData = rowData;
        }
        //创建ResultSetImpl对象
        ResultSetImpl rs = buildResultSetWithRows(callingStatement, catalog,
        		(metadataFromCache == null) ? fields : metadataFromCache,
            rowData, resultSetType, resultSetConcurrency, isBinaryEncoded);



        return rs;
    }
```

MysqlIO.readSingleRowSet

```
//对rowData数据封装
    private RowData readSingleRowSet(long columnCount, int maxRows,
        int resultSetConcurrency, boolean isBinaryEncoded, Field[] fields)
        throws SQLException {
        RowData rowData;
        ArrayList<ResultSetRow> rows = new ArrayList<ResultSetRow>();

        boolean useBufferRowExplicit = useBufferRowExplicit(fields);

        // Now read the data读取数据
        ResultSetRow row = nextRow(fields, (int) columnCount, isBinaryEncoded,
                resultSetConcurrency, false, useBufferRowExplicit, false, null);

        int rowCount = 0;

        if (row != null) {
            rows.add(row);
            rowCount = 1;
        }

        while (row != null) {
        	//读取全部数据到list
        	row = nextRow(fields, (int) columnCount, isBinaryEncoded,
                    resultSetConcurrency, false, useBufferRowExplicit, false, null);

            if (row != null) {
            	if ((maxRows == -1) || (rowCount < maxRows)) {
            		rows.add(row);
            		rowCount++;
            	}
            }
        }

        rowData = new RowDataStatic(rows);

        return rowData;
    }
```

MysqlIO.buildResultSetWithRows

```
//创建ResultSetImpl对象
    private com.mysql.jdbc.ResultSetImpl buildResultSetWithRows(
        StatementImpl callingStatement, String catalog,
        com.mysql.jdbc.Field[] fields, RowData rows, int resultSetType,
        int resultSetConcurrency, boolean isBinaryEncoded)
        throws SQLException {
        ResultSetImpl rs = null;
        //根据传入的ResultSet常量参数生成相应模式的ResultSetImpl
        switch (resultSetConcurrency) {
        case java.sql.ResultSet.CONCUR_READ_ONLY:
            rs = com.mysql.jdbc.ResultSetImpl.getInstance(catalog, fields, rows,
                    this.connection, callingStatement, false);

            if (isBinaryEncoded) {
                rs.setBinaryEncoded();
            }

            break;

        case java.sql.ResultSet.CONCUR_UPDATABLE:
            rs = com.mysql.jdbc.ResultSetImpl.getInstance(catalog, fields, rows,
                    this.connection, callingStatement, true);

            break;

        default:
            return com.mysql.jdbc.ResultSetImpl.getInstance(catalog, fields, rows,
                this.connection, callingStatement, false);
        }

        rs.setResultSetType(resultSetType);
        rs.setResultSetConcurrency(resultSetConcurrency);

        return rs;
    }
```




	`附注`：com.mysql.jdbc.Connection中有一个参数useServerPreparedStmts表明是否使用sql语句预编译功能。
	如果为useServerPreparedStmts=true,则由PreparedStatement的子类ServerPreparedStatement来实现PreparedStatement
	的功能。