package com.loansai.unassisted.di

import com.loansai.unassisted.data.repository.AIRepositoryImpl
import com.loansai.unassisted.data.repository.AppwriteRepositoryImpl
import com.loansai.unassisted.data.repository.BRERepositoryImpl
import com.loansai.unassisted.data.repository.DocumentRepositoryImpl
import com.loansai.unassisted.data.repository.EmployerRepositoryImpl
import com.loansai.unassisted.data.repository.LoanRepositoryImpl
import com.loansai.unassisted.data.repository.MetadataRepositoryImpl
import com.loansai.unassisted.data.repository.ObligationRefinementRepositoryImpl
import com.loansai.unassisted.data.repository.PANRepositoryImpl
import com.loansai.unassisted.data.repository.UserRepositoryImpl
import com.loansai.unassisted.domain.repository.AIRepository
import com.loansai.unassisted.domain.repository.AppwriteRepository
import com.loansai.unassisted.domain.repository.BRERepository
import com.loansai.unassisted.domain.repository.DocumentRepository
import com.loansai.unassisted.domain.repository.EmployerRepository
import com.loansai.unassisted.domain.repository.LoanRepository
import com.loansai.unassisted.domain.repository.MetadataRepository
import com.loansai.unassisted.domain.repository.ObligationRefinementRepository
import com.loansai.unassisted.domain.repository.PANRepository
import com.loansai.unassisted.domain.repository.UserRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Dagger Hilt module to provide repository implementations
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    
    @Binds
    @Singleton
    abstract fun bindUserRepository(
        userRepositoryImpl: UserRepositoryImpl
    ): UserRepository
    
    @Binds
    @Singleton
    abstract fun bindLoanRepository(
        loanRepositoryImpl: LoanRepositoryImpl
    ): LoanRepository
    
    @Binds
    @Singleton
    abstract fun bindPANRepository(
        panRepositoryImpl: PANRepositoryImpl
    ): PANRepository
    
    @Binds
    @Singleton
    abstract fun bindEmployerRepository(
        employerRepositoryImpl: EmployerRepositoryImpl
    ): EmployerRepository
    
    @Binds
    @Singleton
    abstract fun bindDocumentRepository(
        documentRepositoryImpl: DocumentRepositoryImpl
    ): DocumentRepository
    
    @Binds
    @Singleton
    abstract fun bindAIRepository(
        aiRepositoryImpl: AIRepositoryImpl
    ): AIRepository
    
    @Binds
    @Singleton
    abstract fun bindBRERepository(
        breRepositoryImpl: BRERepositoryImpl
    ): BRERepository
    
    @Binds
    @Singleton
    abstract fun bindMetadataRepository(
        metadataRepositoryImpl: MetadataRepositoryImpl
    ): MetadataRepository
    
    @Binds
    @Singleton
    abstract fun bindAppwriteRepository(
        appwriteRepositoryImpl: AppwriteRepositoryImpl
    ): AppwriteRepository
    
    @Binds
    @Singleton
    abstract fun bindObligationRefinementRepository(
        obligationRefinementRepositoryImpl: ObligationRefinementRepositoryImpl
    ): ObligationRefinementRepository
}