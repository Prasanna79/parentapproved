package tv.parentapproved.app.data.cache

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface WhitelistDao {
    @Query("SELECT * FROM app_whitelist ORDER BY display_name ASC")
    suspend fun getAll(): List<WhitelistEntity>

    @Query("SELECT * FROM app_whitelist WHERE whitelisted = 1 ORDER BY display_name ASC")
    suspend fun getWhitelisted(): List<WhitelistEntity>

    @Query("SELECT * FROM app_whitelist WHERE package_name = :packageName")
    suspend fun getByPackageName(packageName: String): WhitelistEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(entities: List<WhitelistEntity>)

    @Query("UPDATE app_whitelist SET whitelisted = :whitelisted WHERE package_name = :packageName")
    suspend fun setWhitelisted(packageName: String, whitelisted: Boolean)

    @Query("SELECT COUNT(*) FROM app_whitelist")
    suspend fun count(): Int

    @Query("DELETE FROM app_whitelist")
    suspend fun deleteAll()
}
