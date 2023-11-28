package com.github.tminglei.slickpg

import org.scalatest.funsuite.AnyFunSuite
import slick.basic.Capability
import slick.jdbc.JdbcCapabilities

import scala.concurrent.Await
import scala.concurrent.duration._

class PgUpsertSuite extends AnyFunSuite with PostgresContainer {

  object MyPostgresProfile extends ExPostgresProfile {
    // Add back `capabilities.insertOrUpdate` to enable native `upsert` support
    override protected def computeCapabilities: Set[Capability] =
      super.computeCapabilities + JdbcCapabilities.insertOrUpdate
  }

  ///---

  import ExPostgresProfile.api._

  lazy val db = Database.forURL(url = container.jdbcUrl, driver = "org.postgresql.Driver")

  case class Bean(id: Long, col1: String, col2: Int)

  class UpsertTestTable(tag: Tag) extends Table[Bean](tag, "test_tab_upsert") {
    def id = column[Long]("id", O.AutoInc, O.PrimaryKey)
    def col1 = column[String]("col1")
    def col2 = column[Int]("col2")

    def * = (id, col1, col2) <> ((Bean.apply _).tupled, Bean.unapply)
  }
  val UpsertTests = TableQuery[UpsertTestTable]

  //------------------------------------------------------------------------------

  test("emulate upsert support") {

    val upsertSql = ExPostgresProfile.compileInsert(UpsertTests.toNode).upsert.sql
    println(s"emulate upsert sql: $upsertSql")

    assert(upsertSql.contains("where not exists"))

    Await.result(db.run(
      DBIO.seq(
        (UpsertTests.schema) create,
        ///
        UpsertTests forceInsertAll Seq(
          Bean(101, "aa", 3),
          Bean(102, "bb", 5),
          Bean(103, "cc", 11)
        ),
        UpsertTests.insertOrUpdate(Bean(101, "a1", 3)),
        UpsertTests.insertOrUpdate(Bean(107, "dd", 7))
      ).andThen(
        DBIO.seq(
          UpsertTests.sortBy(_.id).to[List].result.map(
            r => assert(Seq(
              Bean(1, "dd", 7),
              Bean(101, "a1", 3),
              Bean(102, "bb", 5),
              Bean(103, "cc", 11)
            ) === r)
          )
        )
      ).andFinally(
        (UpsertTests.schema) drop
      ).transactionally
    ), Duration.Inf)
  }

  test("native upsert support") {
    import MyPostgresProfile.api._

    val upsertSql = MyPostgresProfile.compileInsert(UpsertTests.toNode).upsert.sql
    println(s"native upsert sql: $upsertSql")

    assert(upsertSql.contains("on conflict"))

    Await.result(db.run(
      DBIO.seq(
        (UpsertTests.schema) create,
        ///
        UpsertTests forceInsertAll Seq(
          Bean(101, "aa", 3),
          Bean(102, "bb", 5),
          Bean(103, "cc", 11)
        ),
        UpsertTests.insertOrUpdate(Bean(101, "a1", 3)),
        UpsertTests.insertOrUpdate(Bean(107, "dd", 7))
      ).andThen(
        DBIO.seq(
          UpsertTests.sortBy(_.id).to[List].result.map(
            r => assert(Seq(
              Bean(101, "a1", 3),
              Bean(102, "bb", 5),
              Bean(103, "cc", 11),
              Bean(107, "dd", 7)
            ) === r)
          )
        )
      ).andFinally(
        (UpsertTests.schema) drop
      ).transactionally
    ), Duration.Inf)
  }

  test("native bulk upsert support") {
    import MyPostgresProfile.api._

    val upsertSql = MyPostgresProfile.compileInsert(UpsertTests.toNode).upsert.sql
    println(s"native bulk upsert sql: $upsertSql")

    assert(upsertSql.contains("on conflict"))

    Await.result(db.run(
      DBIO.seq(
        (UpsertTests.schema) create,
        ///
        UpsertTests forceInsertAll Seq(
          Bean(101, "aa", 3),
          Bean(102, "bb", 5),
          Bean(103, "cc", 11)
        ),
        UpsertTests insertOrUpdateAll Seq(
          Bean(101, "a1", 3),
          Bean(107, "dd", 7)
        )
      ).andThen(
        DBIO.seq(
          UpsertTests.sortBy(_.id).to[List].result.map(
            r => assert(Seq(
              Bean(101, "a1", 3),
              Bean(102, "bb", 5),
              Bean(103, "cc", 11),
              Bean(107, "dd", 7)
            ) === r)
          )
        )
      ).andFinally(
        (UpsertTests.schema) drop
      ).transactionally
    ), Duration.Inf)
  }

  ///---

  case class Bean1(id: Option[Long], code: String, col2: Int)

  class UpsertTestTable1(tag: Tag) extends Table[Bean1](tag, "test_tab_upsert_1") {
    def id = column[Long]("id", O.AutoInc)
    def code = column[String]("code", O.PrimaryKey)
    def col2 = column[Int]("col2")

    def * = (id.?, code, col2) <> ((Bean1.apply _).tupled, Bean1.unapply)
  }
  val UpsertTests1 = TableQuery[UpsertTestTable1]

  class UpsertTestTable11(tag: Tag) extends Table[Bean1](tag, "test_tab_upsert_11") {
    def id = column[Long]("id", O.AutoInc)
    def code = column[String]("code")
    def col2 = column[Int]("col2")

    def pk = primaryKey("pk_a1", code)
    def * = (id.?, code, col2) <> ((Bean1.apply _).tupled, Bean1.unapply)
  }
  val UpsertTests11 = TableQuery[UpsertTestTable11]

  ///
  test("native upsert support - autoInc + independent pk") {
    import MyPostgresProfile.api._

    val upsertSql = MyPostgresProfile.compileInsert(UpsertTests1.toNode).upsert.sql
    println(s"native upsert sql: $upsertSql")

    assert(upsertSql.contains("on conflict"))

    Await.result(db.run(
      DBIO.seq(
        (UpsertTests1.schema) create,
        ///
        UpsertTests1 forceInsertAll Seq(
          Bean1(Some(101L), "aa", 3),
          Bean1(Some(102L), "bb", 5),
          Bean1(Some(103L), "cc", 11)
        ),
        UpsertTests1.insertOrUpdate(Bean1(None, "aa", 1)),
        UpsertTests1.insertOrUpdate(Bean1(Some(107L), "dd", 7))
      ).andThen(
        DBIO.seq(
          UpsertTests1.sortBy(_.id).to[List].result.map(
            r => assert(Seq(
              Bean1(Some(2), "dd", 7),
              Bean1(Some(101L), "aa", 1),
              Bean1(Some(102L), "bb", 5),
              Bean1(Some(103L), "cc", 11)
            ) === r)
          )
        )
      ).andFinally(
        (UpsertTests1.schema) drop
      ).transactionally
    ), Duration.Inf)
  }

  test("native bulk upsert support - autoInc + independent pk") {
    import MyPostgresProfile.api._

    val upsertSql = MyPostgresProfile.compileInsert(UpsertTests1.toNode).upsert.sql
    println(s"native bulk upsert sql: $upsertSql")

    assert(upsertSql.contains("on conflict"))

    Await.result(db.run(
      DBIO.seq(
        (UpsertTests1.schema) create,
        ///
        UpsertTests1 forceInsertAll Seq(
          Bean1(Some(101L), "aa", 3),
          Bean1(Some(102L), "bb", 5),
          Bean1(Some(103L), "cc", 11)
        ),
        UpsertTests1 insertOrUpdateAll Seq(
          Bean1(None, "aa", 1),
          Bean1(Some(107L), "dd", 7)
        )
      ).andThen(
        DBIO.seq(
          UpsertTests1.sortBy(_.id).to[List].result.map(
            r => assert(Seq(
              Bean1(Some(2), "dd", 7),
              Bean1(Some(101L), "aa", 1),
              Bean1(Some(102L), "bb", 5),
              Bean1(Some(103L), "cc", 11)
            ) === r)
          )
        )
      ).andFinally(
        (UpsertTests1.schema) drop
      ).transactionally
    ), Duration.Inf)
  }


  test("native upsert support - autoInc + pk defined by using `primaryKey`") {
    import MyPostgresProfile.api._

    val upsertSql = MyPostgresProfile.compileInsert(UpsertTests11.toNode).upsert.sql
    println(s"native upsert sql: $upsertSql")

    assert(upsertSql.contains("on conflict"))

    Await.result(db.run(
      DBIO.seq(
        (UpsertTests11.schema) create,
        ///
        UpsertTests11 forceInsertAll Seq(
          Bean1(Some(101L), "aa", 3),
          Bean1(Some(102L), "bb", 5),
          Bean1(Some(103L), "cc", 11)
        ),
        UpsertTests11.insertOrUpdate(Bean1(None, "aa", 1)),
        UpsertTests11.insertOrUpdate(Bean1(Some(107L), "dd", 7))
      ).andThen(
        DBIO.seq(
          UpsertTests11.sortBy(_.id).to[List].result.map(
            r => assert(Seq(
              Bean1(Some(2), "dd", 7),
              Bean1(Some(101L), "aa", 1),
              Bean1(Some(102L), "bb", 5),
              Bean1(Some(103L), "cc", 11)
            ) === r)
          )
        )
      ).andFinally(
        (UpsertTests11.schema) drop
      ).transactionally
    ), Duration.Inf)
  }


  test("native bulk upsert support - autoInc + pk defined by using `primaryKey`") {
    import MyPostgresProfile.api._

    val upsertSql = MyPostgresProfile.compileInsert(UpsertTests11.toNode).upsert.sql
    println(s"native bulk upsert sql: $upsertSql")

    assert(upsertSql.contains("on conflict"))

    Await.result(db.run(
      DBIO.seq(
        (UpsertTests11.schema) create,
        ///
        UpsertTests11 forceInsertAll Seq(
          Bean1(Some(101L), "aa", 3),
          Bean1(Some(102L), "bb", 5),
          Bean1(Some(103L), "cc", 11)
        ),
        UpsertTests11 insertOrUpdateAll Seq(
          Bean1(None, "aa", 1),
          Bean1(Some(107L), "dd", 7)
        )
      ).andThen(
        DBIO.seq(
          UpsertTests11.sortBy(_.id).to[List].result.map(
            r => assert(Seq(
              Bean1(Some(2), "dd", 7),
              Bean1(Some(101L), "aa", 1),
              Bean1(Some(102L), "bb", 5),
              Bean1(Some(103L), "cc", 11)
            ) === r)
          )
        )
      ).andFinally(
        (UpsertTests11.schema) drop
      ).transactionally
    ), Duration.Inf)
  }


  ///
  case class Bean12(id: Option[Long], code: String)

  class UpsertTestTable12(tag: Tag) extends Table[Bean12](tag, "test_tab_upsert_12") {
    def id = column[Long]("id", O.AutoInc)
    def code = column[String]("code", O.PrimaryKey)

    def * = (id.?, code) <> ((Bean12.apply _).tupled, Bean12.unapply)
  }
  val UpsertTests12 = TableQuery[UpsertTestTable12]

  test("native upsert support - autoInc + pk defined by using `primaryKey` w/o other columns") {
    import MyPostgresProfile.api._

    val upsertSql = MyPostgresProfile.compileInsert(UpsertTests12.toNode).upsert.sql
    println(s"native upsert sql: $upsertSql")

    assert(upsertSql.contains("on conflict"))

    Await.result(db.run(
      DBIO.seq(
        (UpsertTests12.schema) create,
        ///
        UpsertTests12 forceInsertAll Seq(
          Bean12(Some(101L), "aa"),
          Bean12(Some(102L), "bb"),
          Bean12(Some(103L), "cc")
        ),
        UpsertTests12.insertOrUpdate(Bean12(None, "aa")),
        UpsertTests12.insertOrUpdate(Bean12(Some(107L), "dd"))
      ).andThen(
        DBIO.seq(
          UpsertTests12.sortBy(_.id).to[List].result.map(
            r => assert(Seq(
              Bean12(Some(2), "dd"),
              Bean12(Some(101L), "aa"),
              Bean12(Some(102L), "bb"),
              Bean12(Some(103L), "cc")
            ) === r)
          )
        )
      ).andFinally(
        (UpsertTests12.schema) drop
      ).transactionally
    ), Duration.Inf)
  }

  test("native bulk upsert support - autoInc + pk defined by using `primaryKey` w/o other columns") {
    import MyPostgresProfile.api._

    val upsertSql = MyPostgresProfile.compileInsert(UpsertTests12.toNode).upsert.sql
    println(s"native bulk upsert sql: $upsertSql")

    assert(upsertSql.contains("on conflict"))

    Await.result(db.run(
      DBIO.seq(
        (UpsertTests12.schema) create,
        ///
        UpsertTests12 forceInsertAll Seq(
          Bean12(Some(101L), "aa"),
          Bean12(Some(102L), "bb"),
          Bean12(Some(103L), "cc")
        ),
        UpsertTests12 insertOrUpdateAll Seq(
          Bean12(None, "aa"),
          Bean12(Some(107L), "dd")
        )
      ).andThen(
        DBIO.seq(
          UpsertTests12.sortBy(_.id).to[List].result.map(
            r => assert(Seq(
              Bean12(Some(2), "dd"),
              Bean12(Some(101L), "aa"),
              Bean12(Some(102L), "bb"),
              Bean12(Some(103L), "cc")
            ) === r)
          )
        )
      ).andFinally(
        (UpsertTests12.schema) drop
      ).transactionally
    ), Duration.Inf)
  }


  ///---
  case class Bean2(id: Long, start: Int, end: Int)

  class UpsertTestTable2(tag: Tag) extends Table[Bean2](tag, "test_tab_upsert2") {
    def id = column[Long]("id", O.AutoInc, O.PrimaryKey)
    def start = column[Int]("start")
    def end = column[Int]("end")

    def * = (id, start, end) <> ((Bean2.apply _).tupled, Bean2.unapply)
  }
  val UpsertTests2 = TableQuery[UpsertTestTable2]

  //------------------------------------------------------------------------------

  test("native upsert support with keyword columns") {
    import MyPostgresProfile.api._

    val upsertSql = MyPostgresProfile.compileInsert(UpsertTests2.toNode).upsert.sql
    println(s"native upsert sql: $upsertSql")

    assert(upsertSql.contains("on conflict"))

    Await.result(db.run(
      DBIO.seq(
        (UpsertTests2.schema) create,
        ///
        UpsertTests2 forceInsertAll Seq(
          Bean2(101, 1, 2),
          Bean2(102, 3, 4),
          Bean2(103, 5, 6)
        ),
        UpsertTests2.insertOrUpdate(Bean2(101, 1, 7)),
        UpsertTests2.insertOrUpdate(Bean2(107, 8, 9))
      ).andThen(
        DBIO.seq(
          UpsertTests2.sortBy(_.id).to[List].result.map(
            r => assert(Seq(
              Bean2(101, 1, 7),
              Bean2(102, 3, 4),
              Bean2(103, 5, 6),
              Bean2(107, 8, 9)
            ) === r)
          )
        )
      ).andFinally(
        (UpsertTests2.schema) drop
      ).transactionally
    ), Duration.Inf)
  }

  test("native bulk upsert support with keyword columns") {
    import MyPostgresProfile.api._

    val upsertSql = MyPostgresProfile.compileInsert(UpsertTests2.toNode).upsert.sql
    println(s"native bulk upsert sql: $upsertSql")

    assert(upsertSql.contains("on conflict"))

    Await.result(db.run(
      DBIO.seq(
        (UpsertTests2.schema) create,
        ///
        UpsertTests2 forceInsertAll Seq(
          Bean2(101, 1, 2),
          Bean2(102, 3, 4),
          Bean2(103, 5, 6)
        ),
        UpsertTests2 insertOrUpdateAll Seq(
          Bean2(101, 1, 7),
          Bean2(107, 8, 9)
        )
      ).andThen(
        DBIO.seq(
          UpsertTests2.sortBy(_.id).to[List].result.map(
            r => assert(Seq(
              Bean2(101, 1, 7),
              Bean2(102, 3, 4),
              Bean2(103, 5, 6),
              Bean2(107, 8, 9)
            ) === r)
          )
        )
      ).andFinally(
        (UpsertTests2.schema) drop
      ).transactionally
    ), Duration.Inf)
  }

}
