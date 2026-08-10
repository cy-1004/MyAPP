package com.myapp.feature.ledger.data

import com.myapp.core.common.contract.LedgerWriter
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 把 [LedgerRepository] 绑定为 [LedgerWriter] 的实现（PRD 4.7.4）。
 *
 * 单绑定（不是 @IntoSet）：LedgerWriter 是单接口契约，不是集合。
 * 其他 feature（如未来的 NotificationListenerService 自动记账）注入 LedgerWriter 时，
 * Hilt 会拿到 LedgerRepository 实例。
 *
 * 删除 :feature:ledger 时只需换一个空实现，调用方一行都不用改。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class LedgerModule {

    @Binds
    @Singleton
    abstract fun bindLedgerWriter(impl: LedgerRepository): LedgerWriter
}
