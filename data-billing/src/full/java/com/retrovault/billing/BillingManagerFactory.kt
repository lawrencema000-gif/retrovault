package com.retrovault.billing

import android.content.Context

/** full build: returns the real Play Billing v7 manager. */
fun createBillingManager(context: Context): BillingManager = PlayBillingManager(context)
