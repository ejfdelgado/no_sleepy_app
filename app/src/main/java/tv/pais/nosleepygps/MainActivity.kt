package tv.pais.nosleepygps

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.firebase.ui.auth.AuthUI
import com.firebase.ui.auth.IdpResponse
import com.google.firebase.auth.FirebaseAuth

class MainActivity : AppCompatActivity() {

    private lateinit var statusTextView: TextView
    private lateinit var actionButton: Button

    private val signInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val response = IdpResponse.fromResultIntent(result.data)
        if (result.resultCode == Activity.RESULT_OK) {
            // Successfully signed in
            updateUI()
        } else {
            // Sign in failed or cancelled
            statusTextView.text = "Sign in failed or cancelled."
            actionButton.text = "Retry Sign In"
            actionButton.setOnClickListener { startSignIn() }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        statusTextView = TextView(this).apply {
            textSize = 20f
            gravity = Gravity.CENTER
            setTextColor(Color.BLACK)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 32) }
        }
        
        actionButton = Button(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        
        layout.addView(statusTextView)
        layout.addView(actionButton)
        
        setContentView(layout)

        updateUI()
    }

    private fun updateUI() {
        val user = FirebaseAuth.getInstance().currentUser
        if (user != null) {
            statusTextView.text = "Welcome, ${user.displayName ?: user.email}!"
            actionButton.text = "Sign Out"
            actionButton.setOnClickListener {
                AuthUI.getInstance()
                    .signOut(this)
                    .addOnCompleteListener {
                        updateUI()
                    }
            }
        } else {
            startSignIn()
        }
    }

    private fun startSignIn() {
        statusTextView.text = "Redirecting to Sign In..."
        actionButton.text = "Sign In"
        actionButton.setOnClickListener { startSignIn() }

        val providers = arrayListOf(
            AuthUI.IdpConfig.EmailBuilder().build(),
            AuthUI.IdpConfig.GoogleBuilder().build()
        )

        val signInIntent = AuthUI.getInstance()
            .createSignInIntentBuilder()
            .setAvailableProviders(providers)
            .build()
        
        signInLauncher.launch(signInIntent)
    }
}
