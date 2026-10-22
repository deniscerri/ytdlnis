package com.deniscerri.ytdl.database

/**
 * Helpers to read big tables in small pages so a single query never overflows the cursor window.
 * Pagination logic lives in the repository layer, DAOs only expose the paged queries.
 */
object Pagination {
    const val PAGE_SIZE = 100

    /** For queries ordered by id ascending (or unordered). [fetch] receives the last seen id and the page size. */
    fun <T> keysetAsc(idOf: (T) -> Long, pageSize: Int = PAGE_SIZE, fetch: (afterId: Long, limit: Int) -> List<T>): List<T> {
        val result = mutableListOf<T>()
        var lastId = Long.MIN_VALUE
        while (true) {
            val page = fetch(lastId, pageSize)
            result.addAll(page)
            if (page.size < pageSize) break
            lastId = idOf(page.last())
        }
        return result
    }

    /** For queries ordered by id descending. [fetch] receives the last seen id and the page size. */
    fun <T> keysetDesc(idOf: (T) -> Long, pageSize: Int = PAGE_SIZE, fetch: (beforeId: Long, limit: Int) -> List<T>): List<T> {
        val result = mutableListOf<T>()
        var lastId = Long.MAX_VALUE
        while (true) {
            val page = fetch(lastId, pageSize)
            result.addAll(page)
            if (page.size < pageSize) break
            lastId = idOf(page.last())
        }
        return result
    }

    /** For queries with any other ordering. [fetch] receives the page size and the offset. */
    fun <T> offset(pageSize: Int = PAGE_SIZE, fetch: (limit: Int, offset: Int) -> List<T>): List<T> {
        val result = mutableListOf<T>()
        var offset = 0
        while (true) {
            val page = fetch(pageSize, offset)
            result.addAll(page)
            if (page.size < pageSize) break
            offset += pageSize
        }
        return result
    }
}