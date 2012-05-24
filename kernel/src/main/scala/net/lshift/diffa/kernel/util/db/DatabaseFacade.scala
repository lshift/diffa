/**
 * Copyright (C) 2010-2012 LShift Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.lshift.diffa.kernel.util.db

/**
 * Abstraction that hopefully lends itself to multiple implementations and allows DiffStore implementations
 * to be slightly less coupled to the underlying database, in terms of access and caching.
 */
trait DatabaseFacade {

  /**
   * Executes the named SELECT query with the specified parameters and binds the results to the expected type.
   */
  def listQuery[T](queryName: String,
                   params: Map[String, Any],
                   firstResult:Option[Int] = None,
                   maxResults:Option[Int] = None): Seq[T]

  /**
   * Executes the named SELECT query for a single row with the specified parameters and binds the results to the expected type.
   * @throws MissingObjectException
   */
  def singleQuery[T](queryName: String,
                     params: Map[String, Any],
                     entityName: String) : T

  def singleQueryMaybe[T](queryName: String,
                          params: Map[String, Any]) : Option[T]

  /**
   * Executes the named UPDATE, DELETE or INSERT query with the specified parameters and the row count
   */
  def execute(queryName: String, params: Map[String, Any]) : Int

  /**
   * Returns a object by id and fails if it does not exist
   * @throws MissingObjectException
   */
  def getOrFail[T](c: Class[T], id: java.io.Serializable, entityName: String) : T

  /**
   * This is a thin wrapper around the Hibernate Session save/1 call.
   * Ideally, we don't want to use this at all, since Hibernate will issue a SELECT to get the new primary back,
   * hence costing us an extra network round trip. However, until we can replace the current key generation wiring
   * in Hibernate, we're going to have to remain dependent on Hibernate.
   */
  @Deprecated def insert[T](o:T) : T

  /**
   * This is a (hopefully) portable wrapper around an underlying transactional resource.
   * This exposes a handle to application code that allows several DB operations to be grouped into one DB transaction.
   * This is marked as deprecated because architecturely speaking, we should ideally refactor the access to
   * the DB to avoid having to group calls.
   */
  @Deprecated def beginTransaction : Transaction
}

/**
 * Encapsulates the name of a DB query and the arguments used in its execution.
 */
case class DatabaseCommand(
  queryName:String,
  params:Map[String, Any]
)

/**
 * This provides a handle that application code can use to group DB calls into one logical unit of work.
 *
 * Usage:
 *
 * val tx = facade.beginTransaction
 *
 * ...
 *
 * tx.execute(command1)
 * tx.execute(command2)
 *
 * ...
 *
 * tx.commit
 *
 */
@Deprecated trait Transaction {

  /**
   * Run a command against the DB and return the row count.
   */
  def execute(command:DatabaseCommand) : Int

  /**
   * This is a thin wrapper around the (deprecated) Hibernate Session save/1 call.
   */
  @Deprecated def insert[T](o:T) : T

  /**
   * Issue the commit command to the database, flushing out all previously submitting commands.
   */
  def commit()

  /**
   * Registers a handler that will be invoked in the event that the transaction is rolled back.
   * This allows application code to perform any necessary cleanup.
   */
  def registerRollbackHandler(h:RollbackHandler)

}

@Deprecated
trait RollbackHandler {

  /**
   * This event is fired when an owning transaction is rolled back.
   */
  def onRollback()
}

