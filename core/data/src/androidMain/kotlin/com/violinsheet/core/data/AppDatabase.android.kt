package com.violinsheet.core.data

import android.content.Context
import androidx.room.Room
import kotlinx.coroutines.Dispatchers

fun appDatabase(context: Context): AppDatabase =
    Room.databaseBuilder<AppDatabase>(context, context.getDatabasePath("violinsheet.db").absolutePath)
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
