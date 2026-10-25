package ca.danielpower.podcast.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Podcast::class, Episode::class, Chapter::class, DailyStats::class], version = 6)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun podcastDao(): PodcastDao
    abstract fun episodeDao(): EpisodeDao
    abstract fun statsDao(): StatsDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "podcast.db")
                // Version 1 shipped only in pre-release test builds, with more than one schema.
                .fallbackToDestructiveMigrationFrom(1)
                .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                .build()
    }
}

/** Adds episode metadata (web page, season/episode numbers, chapters) and the chapters table. */
private val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE episodes ADD COLUMN link TEXT")
        db.execSQL("ALTER TABLE episodes ADD COLUMN seasonNumber INTEGER")
        db.execSQL("ALTER TABLE episodes ADD COLUMN episodeNumber INTEGER")
        db.execSQL("ALTER TABLE episodes ADD COLUMN chaptersUrl TEXT")
        db.execSQL("ALTER TABLE episodes ADD COLUMN chaptersLoaded INTEGER NOT NULL DEFAULT 0")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `chapters` (`episodeId` INTEGER NOT NULL, `startMs` INTEGER NOT NULL, " +
                "`title` TEXT NOT NULL, `url` TEXT, `imageUrl` TEXT, PRIMARY KEY(`episodeId`, `startMs`), " +
                "FOREIGN KEY(`episodeId`) REFERENCES `episodes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_chapters_episodeId` ON `chapters` (`episodeId`)")
    }
}

/** Adds when each episode was last played, for the listening history. */
private val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE episodes ADD COLUMN lastPlayedAt INTEGER")
    }
}

/** Lets a podcast exist unsubscribed, for pages opened from search. */
private val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE podcasts ADD COLUMN subscribed INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE podcasts ADD COLUMN openedAt INTEGER NOT NULL DEFAULT 0")
    }
}

/** Adds the per-day statistics. */
private val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `daily_stats` (`day` INTEGER NOT NULL, `podcastId` INTEGER NOT NULL, " +
                "`listenedMs` INTEGER NOT NULL, `silenceSavedMs` INTEGER NOT NULL, `finished` INTEGER NOT NULL, " +
                "`downloadedBytes` INTEGER NOT NULL, PRIMARY KEY(`day`, `podcastId`))",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_daily_stats_podcastId` ON `daily_stats` (`podcastId`)")
    }
}

class Converters {
    @TypeConverter
    fun fromDownloadState(state: DownloadState): String = state.name

    @TypeConverter
    fun toDownloadState(value: String): DownloadState = DownloadState.valueOf(value)
}
