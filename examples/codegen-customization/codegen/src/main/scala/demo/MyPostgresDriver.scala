package demo

import com.github.tminglei.slickpg._

trait MyPostgresDriver extends ExPostgresProfile
                          with PgArraySupport
                          with PgDateSupportJoda
                          with PgEnumSupport
                          with PgRangeSupport
                          with PgHStoreSupport
                          with PgSearchSupport
                          with PgPostGISSupport {

  override val api = new MyAPI {}

  //////
  trait MyAPI extends ExtPostgresAPI
                with ArrayImplicits
                with Date2DateTimeImplicitsDuration
                with RangeImplicits
                with HStoreImplicits
                with SearchImplicits
                with PostGISImplicits
                with SearchAssistants
}

object MyPostgresDriver extends MyPostgresDriver
