package com.pocketcli.core.security

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SecurityModule {

    @Binds
    @Singleton
    abstract fun bindSecretStore(
        impl: AndroidKeystoreSecretStore
    ): SecretStore

    @Binds
    @Singleton
    abstract fun bindProviderKeyStore(
        impl: SharedPreferencesProviderKeyStore
    ): ProviderKeyStore
}
