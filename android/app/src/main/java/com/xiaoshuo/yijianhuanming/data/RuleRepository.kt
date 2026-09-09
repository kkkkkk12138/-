package com.xiaoshuo.yijianhuanming.data

import kotlinx.coroutines.flow.Flow

interface RuleRepository {
    val rules: Flow<List<ReplaceRule>>

    suspend fun replaceAll(rules: List<ReplaceRule>)
}
