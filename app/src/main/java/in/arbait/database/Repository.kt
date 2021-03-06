package `in`.arbait

import `in`.arbait.database.*
import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import kotlinx.coroutines.*
import java.util.*
import java.util.concurrent.Executors

private const val DATABASE_NAME = "user-database"

class Repository private constructor(context: Context)
{
  val database: Database = Room.databaseBuilder(
    context.applicationContext,
    Database::class.java,
    DATABASE_NAME
  ).addMigrations (
    migration_1_2, migration_2_3, migration_3_4,
    migration_4_5, migration_5_6, migration_6_7,
    migration_7_8, migration_8_9, migration_9_10,
    migration_10_11
  ).build()

  private val userDao = database.userDao()
  private val enrollPermDao = database.enrollingPermissionDao()

  private val executor = Executors.newSingleThreadExecutor()


  suspend fun getEnrollingPermission(userId: Int): EnrollingPermission? {
    return enrollPermDao.get(userId)
  }

  fun updateEnrollingPermission (ep: EnrollingPermission) {
    executor.execute {
      enrollPermDao.update(ep)
    }
  }

  fun addEnrollingPermission (ep: EnrollingPermission) {
    executor.execute {
      enrollPermDao.add(ep)
    }
  }


  suspend fun getUserLastByDate(): User? {
    return userDao.getUserLastByDate()
  }

  suspend fun getUserLastByDate (isConfirmed: Boolean): User? {
    return userDao.getUserLastByDate(isConfirmed)
  }

  fun updateUser (user: User) {
    val now = Calendar.getInstance().time
    user.updatedAt = now
    executor.execute {
      userDao.updateUser(user)
    }
  }

  fun addUser (user: User): Boolean = runBlocking {
    val coroutineValue = GlobalScope.async {
      var error = false
      try {
        userDao.addUser(user)
      } catch (e: SQLiteConstraintException) {
        error = true
      }
      error
    }

    !coroutineValue.await()
  }

  fun deleteUser (user: User) {
    executor.execute {
      userDao.deleteUser(user)
    }
  }

  companion object {
    private var instance: Repository? = null

    fun initialize(context: Context) {
      if (instance == null) {
        instance = Repository(context)
      }
    }

    fun get(): Repository {
      return instance ?:
      throw IllegalStateException("Repository must be initialized")
    }
  }
}