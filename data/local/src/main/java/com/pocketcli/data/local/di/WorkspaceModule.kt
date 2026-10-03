package com.pocketcli.data.local.di

import com.pocketcli.data.local.repository.DefaultWorkspaceStorage
import com.pocketcli.data.local.repository.WorkspaceStorage
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class WorkspaceModule {

    @Binds
    @Singleton
    abstract fun bindWorkspaceStorage(
        impl: DefaultWorkspaceStorage
    ): WorkspaceStorage
}
