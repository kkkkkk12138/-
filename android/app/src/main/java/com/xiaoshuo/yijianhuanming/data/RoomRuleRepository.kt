package com.xiaoshuo.yijianhuanming.data

import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomRuleRepository @Inject constructor(
    private val ruleDao: RuleDao,
) : RuleRepository {
    override val rules: Flow<List<ReplaceRule>> =
        ruleDao.observeAll().map { entities -> entities.map(RuleEntity::toModel) }

    override suspend fun replaceAll(rules: List<ReplaceRule>) {
        ruleDao.replaceAll(normalizeRules(rules).map(ReplaceRule::toEntity))
    }
}
