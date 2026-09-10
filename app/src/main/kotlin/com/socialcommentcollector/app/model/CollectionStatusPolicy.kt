package com.socialcommentcollector.app.model

object CollectionStatusPolicy {
    fun canTransition(from: CollectionStatus, to: CollectionStatus): Boolean = when (from) {
        CollectionStatus.QUEUED -> to == CollectionStatus.COLLECTING
        CollectionStatus.COLLECTING -> to == CollectionStatus.COMPLETED ||
            to == CollectionStatus.INCOMPLETE || to == CollectionStatus.FAILED
        CollectionStatus.COMPLETED, CollectionStatus.INCOMPLETE, CollectionStatus.FAILED -> false
    }
}
