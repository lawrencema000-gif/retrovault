package com.retrovault.billing

import android.content.Context

/** foss build: no Play Billing. Returns the free, no-purchase manager. */
fun createBillingManager(context: Context): BillingManager = FreeBillingManager(context)
