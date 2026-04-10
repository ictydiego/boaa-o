package br.unasp.boacao

import android.app.Application
import br.unasp.boacao.data.repository.*
import br.unasp.boacao.util.NotificationHelper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class BoaAcaoApplication : Application() {

    lateinit var authRepository: AuthRepository
        private set

    lateinit var donorRepository: DonorRepository
        private set

    lateinit var volunteerRepository: VolunteerRepository
        private set

    lateinit var beneficiaryRepository: BeneficiaryRepository
        private set

    lateinit var pointsRepository: PointsRepository
        private set

    lateinit var giftCardRepository: GiftCardRepository
        private set

    lateinit var rankingRepository: RankingRepository
        private set

    override fun onCreate() {
        super.onCreate()
        val auth = FirebaseAuth.getInstance()
        val firestore = FirebaseFirestore.getInstance()

        authRepository = AuthRepositoryImpl(auth, firestore)
        donorRepository = DonorRepositoryImpl(firestore)
        volunteerRepository = VolunteerRepositoryImpl(firestore)
        beneficiaryRepository = BeneficiaryRepositoryImpl(firestore)
        pointsRepository = PointsRepositoryImpl(firestore)
        giftCardRepository = GiftCardRepositoryImpl(firestore)
        rankingRepository = RankingRepositoryImpl(firestore)

        NotificationHelper.createNotificationChannel(this)
    }
}
