package com.ueiol.yixue.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        SubjectEntity::class,
        SessionEntity::class,
        MessageEntity::class,
        QuizEntity::class,
        QuestionEntity::class,
        AnswerRecordEntity::class,
        WrongQuestionEntity::class,
        NoteEntity::class,
        TextbookEntity::class,
        TextbookChunkEntity::class
    ],
    version = 2,
    exportSchema = true
)
abstract class YixueDatabase : RoomDatabase() {
    abstract fun subjectDao(): SubjectDao
    abstract fun sessionDao(): SessionDao
    abstract fun messageDao(): MessageDao
    abstract fun quizDao(): QuizDao
    abstract fun questionDao(): QuestionDao
    abstract fun answerRecordDao(): AnswerRecordDao
    abstract fun wrongQuestionDao(): WrongQuestionDao
    abstract fun noteDao(): NoteDao
    abstract fun textbookDao(): TextbookDao

    companion object {
        /** v2：新增教材表与教材分块表（不清空用户已有数据）。 */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `textbooks` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `subjectId` INTEGER NOT NULL,
                        `title` TEXT NOT NULL,
                        `fileName` TEXT NOT NULL,
                        `isExactMatch` INTEGER NOT NULL,
                        `chunkCount` INTEGER NOT NULL,
                        `charCount` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_textbooks_subjectId` ON `textbooks` (`subjectId`)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `textbook_chunks` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `textbookId` INTEGER NOT NULL,
                        `seq` INTEGER NOT NULL,
                        `page` INTEGER NOT NULL,
                        `content` TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_textbook_chunks_textbookId` ON `textbook_chunks` (`textbookId`)")
            }
        }

        // 不用 fallbackToDestructiveMigration：缺迁移时直接崩溃，把问题暴露在开发期，
        // 而不是静默清空用户的对话/笔记/错题数据
        fun build(context: Context): YixueDatabase =
            Room.databaseBuilder(context, YixueDatabase::class.java, "yixue.db")
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
