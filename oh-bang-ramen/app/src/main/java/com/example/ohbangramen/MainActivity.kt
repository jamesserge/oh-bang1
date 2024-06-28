package com.example.ohbangramen

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.navigation.compose.rememberNavController
import androidx.room.Room
import com.example.ohbangramen.MenuNetwork.MenuItemNetwork
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.http.ContentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var menuViewModel: MenuViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val sharedPreferences : SharedPreferences = getSharedPreferences("user_prefs", Context.MODE_PRIVATE)

        menuViewModel = ViewModelProvider(this).get(MenuViewModel::class.java)

        setContent {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        val MyNavigation = rememberNavController()
                        Navigation( navController = MyNavigation, sharedPreferences, menuViewModel )
                    }
                }
        }
    }
}


class MenuViewModel(application: Application) : AndroidViewModel(application) {
    private val database by lazy {
        Room.databaseBuilder(application, AppDatabase::class.java, "database")
            .fallbackToDestructiveMigration() // Needed to update database version.
            .build()
    }

    val menuItems: LiveData<List<MenuItemRoom>> = database.menuItemDao().getAll()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            if (isNetworkAvailable() && isMenuEmpty()) {
                val menuItemsNetwork = fetchMenu()
                saveMenuToDatabase(menuItemsNetwork)
            }
        }
    }

    private fun saveMenuToDatabase(menuItemsNetwork: List<MenuItemNetwork>) {
        val menuItemRoom = menuItemsNetwork.map { it.toMenuItemRoom() }
        viewModelScope.launch(Dispatchers.IO) {
            database.menuItemDao().insertAll(*menuItemRoom.toTypedArray())
        }
    }

    private suspend fun isMenuEmpty() : Boolean {
        return database.menuItemDao().isEmpty()
    }

    private suspend fun fetchMenu() : List<MenuItemNetwork> {
        val httpClient = HttpClient(Android) {
            install(ContentNegotiation) {
                json(contentType = ContentType("text", "plain"))
            }
        }
        return httpClient.get("https://raw.githubusercontent.com/Meta-Mobile-Developer-PC/Working-With-Data-API/main/menu.json")
            .body<MenuNetwork>()
            .menu
    }

    fun isNetworkAvailable(): Boolean {
        return try {
            val command = "ping -c 1 google.com"
            val process = Runtime.getRuntime().exec(command)
            val exitValue = process.waitFor()
            exitValue == 0
        } catch (e: Exception) {
            false
        }
    }

    fun getFilteredMenuItems(searchPhrase: String, category: String) : LiveData<List<MenuItemRoom>> {
        return database.menuItemDao().getFilteredMenuItems("%$searchPhrase%", category)
    }
    // Retrieve MenuItemRoom by ID
    fun getMenuItemById(itemId: Int) : LiveData<List<MenuItemRoom>> {
        return database.menuItemDao().getMenuItemById(itemId)
    }
}



