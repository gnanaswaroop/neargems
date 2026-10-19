package com.nearwise.android.car

import android.content.Context
import androidx.car.app.connection.CarConnection
import androidx.lifecycle.Observer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn

/** Emits true while the phone is projecting to Android Auto. */
class CarConnectionMonitor(context: Context) {
    private val carConnection = CarConnection(context.applicationContext)

    val projecting: Flow<Boolean> = callbackFlow {
        val observer = Observer<Int> { type -> trySend(type == CarConnection.CONNECTION_TYPE_PROJECTION) }
        carConnection.type.observeForever(observer)
        awaitClose { carConnection.type.removeObserver(observer) }
    }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Main)
}
