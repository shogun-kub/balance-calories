package com.balancecalories.data.db

import android.content.Context
import androidx.room.Room

/**
 * The only place that knows how the database is built. `:app` asks for a database rather than importing
 * Room, so the persistence library stays an implementation detail of this module.
 */
fun createDatabase(
    context: Context,
    name: String = BalanceCaloriesDatabase.NAME,
): BalanceCaloriesDatabase = Room.databaseBuilder(
    context.applicationContext,
    BalanceCaloriesDatabase::class.java,
    name,
).build()
