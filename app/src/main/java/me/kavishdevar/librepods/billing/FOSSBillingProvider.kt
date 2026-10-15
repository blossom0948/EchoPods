/*
    LibrePods - AirPods liberated from Apple’s ecosystem
    Copyright (C) 2025 LibrePods contributors

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>.
*/

package me.kavishdevar.librepods.billing

import android.app.Activity
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import me.kavishdevar.librepods.R

class FOSSBillingProvider(context: Context): BillingProvider {
    // This derivative is distributed as a free-and-open-source build. Keep
    // every app-level feature enabled rather than mirroring upstream's
    // optional contribution unlock.
    private val _isPremium = MutableStateFlow(true)
    override val isPremium: StateFlow<Boolean> = _isPremium

    private val _price = MutableStateFlow(context.getString(R.string.name_your_own_price))
    override val price: StateFlow<String> = _price

    init {
        queryPurchases()
    }

    override fun purchase(activity: Activity) = Unit

    override fun queryPurchases() {
        _isPremium.value = true
    }

    override fun restorePurchases() {
        _isPremium.value = true
    }
}
