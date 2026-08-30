package com.example.spendtrack
import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName="expenses", indices=[Index(value=["notificationHash"], unique=true)])
data class Expense(
 @PrimaryKey(autoGenerate=true) val id:Long=0,
 val amount:Double, val merchant:String, val category:String, val date:String,
 val source:String="MANUAL", val notificationHash:String?=null
)
@Dao interface ExpenseDao {
 @Query("SELECT * FROM expenses ORDER BY id DESC") fun all():Flow<List<Expense>>
 @Insert suspend fun insert(e:Expense):Long
 @Update suspend fun update(e:Expense)
 @Delete suspend fun delete(e:Expense)
 @Query("SELECT EXISTS(SELECT 1 FROM expenses WHERE notificationHash=:hash)") suspend fun hasHash(hash:String):Boolean
}
@Database(entities=[Expense::class],version=1,exportSchema=false)
abstract class AppDb:RoomDatabase(){abstract fun dao():ExpenseDao
 companion object { @Volatile private var I:AppDb?=null
 fun get(c:Context)=I?:synchronized(this){I?:Room.databaseBuilder(c.applicationContext,AppDb::class.java,"spend.db").build().also{I=it}}
}}
