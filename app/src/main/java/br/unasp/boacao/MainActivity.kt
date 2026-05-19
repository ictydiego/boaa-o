package br.unasp.boacao

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import br.unasp.boacao.presentation.navigation.AppNavigation
import br.unasp.boacao.ui.theme.BoaAcaoTheme

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BoaAcaoTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    // Aqui chamamos o arquivo que tem todas as rotas configuradas
                    AppNavigation()
                }
            }
        }
    }
}
