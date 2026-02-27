# **GRIDGAIN 9 TRANSACTION MODEL - COMPLETE DETAILED SUMMARY**

## **1. TRANSACTION ARCHITECTURE & FUNDAMENTAL PRINCIPLES**

### **1.1 Core Transaction Model**

- **Strictly Serializable Transactions**: GridGain 9 uses the highest level of transaction isolation - strict serializability. This means all transactions appear to execute in a completely serial order, as if they were running one after another.
- **All Tables Are Transactional by Default**: Unlike GridGain 8 where atomicity modes had to be explicitly configured, all tables in GridGain 9 are transactional without performance penalty.
- **MVCC-Based (Multi-Version Concurrency Control)**: All transactions use MVCC, which creates logical snapshots of data for each transaction, allowing high concurrency.
- **Lock-Free Reads**: Read operations don't require locks, supporting massive application concurrency and scalability.
- **No Deadlocks**: MVCC design eliminates deadlocks entirely.


### **1.2 ACID Guarantees**

- **Atomicity**: All operations in a transaction succeed or all fail - no partial execution.
- **Consistency**: Strictly serializable model ensures the highest level of consistency.
- **Isolation**: Strict serializability guarantees complete isolation between transactions.
- **Durability**: On-disk persistence with ACID compliance, using WAL (Write-Ahead Log) and checkpointing mechanisms.

***


## **2. TRANSACTION PROTOCOL & CONSENSUS**

### **2.1 RAFT Consensus Algorithm**

GridGain 9 uses RAFT as the foundational mechanism for maintaining consistency:

**Node States:**

- **Leader**: Handles all client requests and manages log replication (one per RAFT group)
- **Follower**: Replicates data from leader and participates in voting
- **Candidate**: Transitional state during leader election
- **Learner**: Only replicates data, doesn't vote

**Quorum Requirements:**

| Total Nodes | Quorum Required | Tolerates  |
| ----------- | --------------- | ---------- |
| 1           | 1               | 0 failures |
| 2           | 2               | 0 failures |
| 3           | 2               | 1 failure  |
| 5           | 3               | 2 failures |
| 7           | 4               | 3 failures |

**Key RAFT Groups:**

1. **Metastorage Group**: Handles table/index definitions, distribution zone configs, cluster topology
2. **Cluster Management Group**: Manages current cluster state, coordinates node join/leave operations
3. **Partition RAFT Groups**: Each partition is assigned to a RAFT group for data updates and replication


### **2.2 Transaction Coordination**

- Uses RAFT for distributed consensus on transaction commits
- Partition-specific RAFT logs record elections and consensus activity
- Log replication ensures all nodes converge on same state in same order
- Split-brain protection is built-in (no additional configuration needed)

***


## **3. TRANSACTION APIS**

### **3.1 SQL Transactions**

**Starting Transactions:**

sql

```sql
START TRANSACTION [READ ONLY | READ WRITE];
```

**Transaction Modes:**

- `READ WRITE`: Both read and write operations allowed (default)
- `READ ONLY`: Only read operations allowed

**Committing/Rolling Back:**

sql

```sql
COMMIT;
ROLLBACK [TRANSACTION];
```

**Important Constraints:**

- DDL statements are NOT supported inside transactions
- Transaction control statements only allowed within SQL scripts
- Cannot mix transactional and non-transactional operations

**Example:**

sql

```sql
START TRANSACTION READ WRITE;
INSERT INTO Person (id, name, surname) VALUES (1, 'John', 'Smith');
INSERT INTO Person (id, name, surname) VALUES (2, 'Jane', 'Smith');
INSERT INTO Person (id, name, surname) VALUES (3, 'Adam', 'Mason');
COMMIT;
```


### **3.2 Java Table API Transactions**

**All table operations are transactional** - you can provide explicit transaction or use implicit:

java

```java
// Implicit transaction (created automatically)
RecordView<Tuple> view = table.recordView();
view.upsert(null, recordTuple); // null = implicit transaction

// Explicit transaction (passed as first parameter)
IgniteTransactions tx = client.transactions();
Transaction transaction = tx.begin();
try {
    view.upsert(transaction, recordTuple);
    transaction.commit();
} catch (Exception e) {
    transaction.rollback();
    throw e;
}
```

**Transaction Parameter:**

- First argument of any Table API call (`null` for implicit transactions)
- First argument of SQL API calls
- Works with RecordView, KeyValueView, and all view types


### **3.3 JDBC Driver Transactions**

java

```java
Connection conn = DriverManager.getConnection("jdbc:ignite:thin://127.0.0.1:10800");
conn.setAutoCommit(false); // Disable auto-commit

try {
    // Execute operations
    Statement stmt = conn.createStatement();
    stmt.execute("INSERT INTO Person VALUES (1, 'John', 30)");
    stmt.execute("UPDATE City SET population = population + 1");
    
    conn.commit();
} catch (Exception e) {
    conn.rollback();
}
```


### **3.4 Python DB API Transactions**

python

```python
conn = pygridgain_dbapi.connect(address=['127.0.0.1:10800'])
conn.autocommit = False  # Manual transaction handling

cursor = conn.cursor()
try:
    cursor.execute('INSERT INTO Person VALUES(?, ?, ?)', [4, "Alice", 29])
    cursor.execute('INSERT INTO Person VALUES(?, ?, ?)', [5, "Charlie", 31])
    conn.commit()
except Exception as e:
    conn.rollback()
```


### **3.5 .NET Client Transactions**

csharp

```csharp
IResultSet<IIgniteTuple> resultSet = await client.Sql.ExecuteAsync(
    transaction: null,  // null for implicit transaction
    "select name from tbl where id = ?", 
    42
);

// All Table API operations accept transaction as first parameter
await view.UpsertAsync(transaction: null, fullRecord);
```

***


## **4. TRANSACTION ISOLATION & CONCURRENCY**

### **4.1 Isolation Level**

**GridGain 9 provides:**

- **Strict Serializability**: Only isolation level (no configuration needed)
- Transactions appear to execute serially, one after another
- Prevents all anomalies: dirty reads, non-repeatable reads, phantom reads, write skew

**Comparison to GridGain 8:**

- GridGain 8 had: READ\_COMMITTED, REPEATABLE\_READ, SERIALIZABLE with OPTIMISTIC/PESSIMISTIC concurrency
- GridGain 9 simplified to: Single strictly serializable model with MVCC


### **4.2 MVCC Implementation Details**

**Snapshot Isolation:**

- Each transaction obtains consistent snapshot at start
- Can only view/modify data in that snapshot
- New versions created on update (not in-place modification)
- New version visible only when transaction commits

**Version Management:**

- Multiple versions of same data coexist temporarily
- Old versions cleaned up by garbage collection
- No physical snapshots - logical snapshots coordinated by cluster

**Conflict Detection:**

- System verifies entry hasn't been updated by other transactions
- If conflict detected, transaction fails with exception
- Must retry transaction (application responsibility)


### **4.3 Lock-Free Reads**

**Key Feature:**

- Read operations don't acquire locks
- Readers don't block writers
- Writers don't block readers
- Massive concurrency for read-heavy workloads
- Read-only transactions optimized for performance

***


## **5. DISTRIBUTED TRANSACTION HANDLING**

### **5.1 Data Distribution & Replication**

**Partitioning:**

- Tables partitioned according to distribution zone configuration
- Each partition assigned to RAFT group
- RAFT groups shared by all tables in same distribution zone

**Replication:**

- Data replicated via RAFT consensus
- Primary replica typically on leader node
- Backup replicas on follower nodes
- Configurable replication factor (number of replicas)

**Split-Brain Protection:**

- Built-in via RAFT quorum requirements
- Minority partition becomes read-only/unavailable
- Only partition with majority can elect leader and continue operations


### **5.2 Transaction Commit Protocol**

While specific 2PC details for GridGain 9 aren't extensively documented, the system:

- Uses RAFT for distributed consensus on commits
- Coordinates across multiple partitions/nodes
- Ensures atomicity across distributed data
- Leverages RAFT's log replication for durability


### **5.3 Failure Handling**

**Node Failures:**

- RAFT automatically handles node failures
- Leader election occurs if leader fails
- Transactions continue if quorum maintained
- Failed transactions rolled back automatically

**Network Partitions:**

- Minority partition cannot commit transactions
- Majority partition continues operations
- Reconciliation occurs when partition heals

***


## **6. IMPLICIT VS EXPLICIT TRANSACTIONS**

### **6.1 Implicit Transactions**

**Behavior:**

- Created automatically for each operation
- Used when `transaction: null` or `null` passed
- Committed immediately after operation
- Each operation is atomic

**When Used:**

- Single-operation scenarios
- Auto-commit mode (SQL connections)
- When no explicit transaction provided


### **6.2 Explicit Transactions**

**Behavior:**

- Must be explicitly started and committed/rolled back
- Group multiple operations atomically
- Span multiple tables/partitions
- Require manual lifecycle management

**Best Practices:**

- Use for multi-operation atomic units
- Always close in finally block or use try-with-resources
- Handle exceptions and rollback appropriately

***


## **7. TRANSACTION LIFECYCLE & MANAGEMENT**

### **7.1 Transaction States**

- **Active**: Transaction executing operations
- **Preparing**: Coordinating commit across nodes (RAFT consensus)
- **Committed**: Successfully committed
- **Rolled Back**: Aborted/failed


### **7.2 Transaction Timeouts**

- Configurable timeout for transactions
- TimeoutException if exceeded
- Automatic rollback on timeout


### **7.3 Transaction Exceptions**

Common exceptions:

- **TransactionTimeoutException**: Transaction timed out
- **TransactionRollbackException**: Automatically rolled back
- **TransactionConflictException**: MVCC conflict detected
- **CacheException**: Write conflict in MVCC (with rollbackOnly flag)

***


## **8. SQL ENGINE & TRANSACTION INTEGRATION**

### **8.1 Apache Calcite Integration**

- GridGain 9 uses Apache Calcite SQL engine
- Optimizes distributed query execution
- Handles transaction-aware query planning
- Supports complex queries within transactions


### **8.2 SQL Capabilities**

- Full ANSI SQL 2016 compliance (up from SQL 99 in GridGain 8)
- Distributed JOIN operations
- Subqueries and aggregations
- All within transactional context

***


## **9. STORAGE & PERSISTENCE**

### **9.1 Pluggable Storage Engines**

GridGain 9 supports multiple storage engines (all transactional):

- **In-Memory (AIMEM)**: Memory-only storage
- **Native Persistent (AIPERSIST)**: Native persistence with WAL
- **RocksDB**: RocksDB-based persistent storage
- **Columnar**: For analytical workloads


### **9.2 Write-Ahead Log (WAL)**

- All updates appended to WAL before applying to data files
- Ensures durability
- Used for recovery after crashes
- Checkpointing periodically flushes changes to data files


### **9.3 Data Format**

- Binary Tuple format (replaces Binary Object from GridGain 8)
- More efficient serialization/deserialization
- Reduced overhead
- Same format in-memory and on-disk

***


## **10. PERFORMANCE CHARACTERISTICS**

### **10.1 Performance Benefits**

- Lock-free reads enable high read concurrency
- MVCC reduces contention compared to lock-based systems
- Strict serializability simplifies application logic
- No deadlock detection overhead (deadlocks impossible)


### **10.2 Performance Considerations**

- MVCC requires garbage collection of old versions
- Snapshot coordination adds slight overhead
- Network round-trips for distributed commits
- RAFT consensus requires majority quorum


### **10.3 Read vs Write Performance**

- **Read-Only Transactions**: Lightweight, optimized, lock-free
- **Read-Write Transactions**: Slightly higher overhead for MVCC versioning
- **Large Transactions**: May hold multiple versions in memory

***


## **11. KEY DIFFERENCES FROM GRIDGAIN 8**

| Aspect                       | GridGain 8                                      | GridGain 9                          |
| ---------------------------- | ----------------------------------------------- | ----------------------------------- |
| **Atomicity Mode**           | Must configure TRANSACTIONAL                    | All tables transactional by default |
| **Isolation Levels**         | READ\_COMMITTED, REPEATABLE\_READ, SERIALIZABLE | Strictly serializable only          |
| **Concurrency Modes**        | OPTIMISTIC, PESSIMISTIC                         | MVCC-based (no explicit modes)      |
| **Deadlocks**                | Possible (detection required)                   | Impossible (MVCC design)            |
| **Consensus**                | Custom                                          | RAFT-based                          |
| **Configuration Complexity** | High (many options)                             | Low (sensible defaults)             |
| **SQL Transactions**         | Limited support                                 | Full support across all APIs        |

***


## **12. USAGE PATTERNS & BEST PRACTICES**

### **12.1 When to Use Explicit Transactions**

- Multiple operations must be atomic
- Operations span multiple tables
- Need to ensure consistency across operations
- Coordinating reads and writes


### **12.2 When Implicit is Fine**

- Single-operation scenarios
- Independent operations
- High-throughput write scenarios with no coordination needed


### **12.3 Error Handling**

java

```java
try (Transaction tx = client.transactions().begin()) {
    // Perform operations
    table.recordView().upsert(tx, record);
    tx.commit();
} catch (TransactionConflictException e) {
    // Retry logic
} catch (TransactionTimeoutException e) {
    // Handle timeout
} catch (Exception e) {
    // Handle other errors (transaction already rolled back)
}
```


### **12.4 Optimization Tips**

- Use read-only transactions when possible (lighter weight)
- Keep transactions short (reduce version retention)
- Avoid long-running transactions (hold versions in memory)
- Batch operations when appropriate

***


## **13. LIMITATIONS & CONSTRAINTS**

### **13.1 Current Limitations**

- DDL statements not supported inside transactions
- No custom isolation level configuration (strict serializability only)
- No explicit control over MVCC versioning/cleanup
- Transaction control statements only in SQL scripts (not single statements)


### **13.2 Design Trade-offs**

- Simplicity vs Flexibility: GridGain 9 chose simplicity (single isolation level)
- Consistency vs Performance: Chose strong consistency (may have slight performance cost vs eventual consistency)
- Safety vs Control: Eliminated deadlocks by removing pessimistic/optimistic choice

***


## **14. MONITORING & DEBUGGING**

### **14.1 Transaction Metrics**

- Available through system views
- Metrics on active transactions
- Commit/rollback rates
- Conflict rates


### **14.2 System Views**

GridGain 9 provides system views for monitoring:

- Transaction status
- RAFT group states
- Partition replica status

***


## **15. FUTURE CONSIDERATIONS**

Based on release notes, GridGain continues to evolve:

- Performance improvements planned
- Enhanced transaction monitoring
- Additional optimization for specific workloads
