package com.example.top10downloader

import android.content.Context
import android.os.AsyncTask
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.ListView
import kotlinx.android.synthetic.main.activity_main.*
import java.io.IOException
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.URL
import kotlin.properties.Delegates

class FeedEntry {
    var name: String = ""
    var artist: String = ""
    var releaseDate: String = ""
    var summary: String = ""
    var imageURL: String = ""

    override fun toString(): String {
        return """
            name = $name
            artist = $artist
            releaseDate = $releaseDate
            imageURL = $imageURL
            """.trimIndent()
    }


}

class MainActivity : AppCompatActivity() {
    private val TAG = "MainActivity"
    private var feedUrl: String = "http://ax.itunes.apple.com/WebObjects/MZStoreServices.woa/ws/RSS/topfreeapplications/limit=%d/xml"
    private var feedLimit = 10
    //Kan inte använda pga att AsyncTask kan inte göra flera async på samma val, då måste jag skapa ny. T ex genom nullable.
    //private val downloadData by lazy { DownloadData(this, xmlListView) }
    private var downloadData: DownloadData? = null

    private var feedCachedUrl = "INVALIDATED"
    private val STATE_URL = "feedUrl"
    private val STATE_LIMIT = "feedLimit"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Log.d(TAG, "onCreate called")
        if(savedInstanceState != null) {
            feedUrl = savedInstanceState.getString(STATE_URL)
            feedLimit = savedInstanceState.getInt(STATE_LIMIT)
        }

        downloadUrl(feedUrl.format(feedLimit))
        Log.d(TAG, "OnCreate: Done")
    }

    private fun downloadUrl(feedUrl: String) {
        if(feedUrl != feedCachedUrl) {
            Log.d(TAG, "downloadUrl starting AsyncTask")
            downloadData = DownloadData(this, xmlListView)
            downloadData?.execute(feedUrl)
            feedCachedUrl = feedUrl
            Log.d(TAG, "downloadUrl: done")
        } else {
            Log.d(TAG, "downloadUrl - URL not changed")
        }

    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.feeds_menu, menu)

        if(feedLimit == 10) {
            menu?.findItem(R.id.mnu10)?.isChecked = true
        } else {
            menu?.findItem(R.id.mnu25)?.isChecked = true
        }
        return true
    }
//Ändrade item: MenuItem? till item: MenuItem, gör bara detta om du är säker på att den inte kan bli null. Det kan jag här pga om jag väljer en menuItem, så
//Har jag redan kodat in den och då måste den finnas.
    override fun onOptionsItemSelected(item: MenuItem): Boolean {


        when (item.itemId) {
            R.id.mnuFree ->
                    feedUrl = "http://ax.itunes.apple.com/WebObjects/MZStoreServices.woa/ws/RSS/topfreeapplications/limit=%d/xml"
            R.id.mnuPaid ->
                    feedUrl = "http://ax.itunes.apple.com/WebObjects/MZStoreServices.woa/ws/RSS/toppaidapplications/limit=%d/xml"
            R.id.mnuSongs ->
                    feedUrl = "http://ax.itunes.apple.com/WebObjects/MZStoreServices.woa/ws/RSS/topsongs/limit=%d/xml"
            R.id.mnu10, R.id.mnu25 -> {
                if(!item.isChecked) {
                    item.isChecked = true
                    feedLimit = 35-feedLimit
                    Log.d(TAG, "onOptionsItemSelected: ${item.title} setting feedLimit to $feedLimit")
                } else {
                    Log.d(TAG, "onOptionsItemSelected: ${item.title} setting feedLimit to unchanged")

                }
            }
            R.id.mnuRefresh -> feedCachedUrl = "INVALIDATED"
            else ->
                return super.onOptionsItemSelected(item)
        }

    downloadUrl(feedUrl.format(feedLimit))
        return true
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_URL, feedUrl)
        outState.putInt(STATE_LIMIT, feedLimit)
    }

    override fun onDestroy() {
        super.onDestroy()
        downloadData?.cancel(true)
    }

    companion object {
        //AsyncTask<String = url, Void = Ifall vi vill ha progressbar, String = Type of result vi vill ha tillbaka
         class DownloadData(context: Context, listView: ListView) : AsyncTask<String, Void, String>() {
            private val TAG = "DownloadData"

            //Detta ger upphov till leaks, använd istället det under (tänk om vyn har förstörts?) Då är det konstigt om jag har context till vy som ej finns.
           // var propContext = context
            //var prolListView = listView

            var propContext: Context by Delegates.notNull()
            var propListView: ListView  by Delegates.notNull()

            init {
                propContext = context
                propListView = listView
            }

            override fun onPostExecute(result: String) {
                super.onPostExecute(result)
               // Log.d(TAG, "onPostExecute: Parameter is $result")
                val parseApplications = ParseApplications()
                parseApplications.parse(result)

                //context describes the application environment, or in this case an activitys environment.
                //It knows about such things as the screen which the activity is using and other information.

               // val arrayAdapter = ArrayAdapter<FeedEntry>(propContext, R.layout.list_item, parseApplications.applications)
               // propListView.adapter = arrayAdapter
                val feedAdapter = FeedAdapter(propContext, R.layout.list_record, parseApplications.applications)
                propListView.adapter = feedAdapter
            }

            override fun doInBackground(vararg url: String?): String {
                Log.d(TAG, "doInBackgroiund;: Starts with ${url[0]}")
                val rssFeed = downloadXML(url[0])
                if (rssFeed.isEmpty()) {
                    Log.e(TAG, "doInBackground: Error Downloading")

                }
                return rssFeed
            }
            //Allt detta kan skrivas som
            // private fun downloadXML(urlPath: String?): String {
            //      return URL(urlPath).readText()
            //}
            private fun downloadXML(urlPath: String?): String {
                val xmlResult = StringBuilder()

                try {
                    val url = URL(urlPath)
                    val connection: HttpURLConnection = url.openConnection() as HttpURLConnection
                    val response = connection.responseCode
                    Log.d(TAG, "downloadXML: the response code was $response")

                    /*      val inputStream = connection.inputStream
                          val inputStreamReader = InputStreamReader(inputStream)
                          val reader = BufferedReader(inputStreamReader)   */

                    //läser in rssfeeden i form  av chars, jag sätter buffer till 500, och sen lämnar den när charsread är mindre än 0 ?

                    //                    val reader = BufferedReader(InputStreamReader(connection.inputStream))
//
//                    val inputBuffer = CharArray(500)
//                    var charsRead = 0
//                    while (charsRead >= 0) {
//                        charsRead = reader.read(inputBuffer)
//                        if (charsRead > 0) {
//                            xmlResult.append(String(inputBuffer, 0, charsRead))
//                        }
//                    }
//
//                    reader.close()

//          Annat sätt att skriva ovanstående på, mycket mer functional programming
                    //                 val stream = connection.inputStream
                    connection.inputStream.buffered().reader().use { reader ->
                        xmlResult.append(reader.readText())
                    }


                    Log.d(TAG, "RECEIVED ${xmlResult.length} bytes")
                    return xmlResult.toString()

//                    //VIKTIGT MED ORDNINGEN, MalformedUrlException extendar IOException, så den ska vara först.
//                } catch (e: MalformedURLException){
//                    Log.e(TAG, "DOWNLOADXML: INVALID URL ${e.message}")
//                } catch (e: IOException) {
//                    Log.e(TAG, "DOWNLOADXML: IOEXCEPTION READING DATA: ${e.message}")
//                } catch (e: SecurityException) {
//                    e.printStackTrace()
//                    Log.e(TAG, "downloadXML: SECURITY EXCEPTION. NEEDS PERMISSION. ${e.message}")
//                } catch (e: Exception) { //Fångar alla andra exceptions
//                    Log.e(TAG, "UNKNOWN ERROR: ${e.message}")
//                }
                    //Kotlin Kod, mycket snyggare sätt att skriva.
                } catch (e: Exception) {
                    val errorMessage = when (e) {
                        is MalformedURLException -> "downloadXML: Invalid URL ${e.message}"
                        is IOException -> "downloadXML: IO Exception reading data ${e.message}"
                        is SecurityException -> {
                            e.printStackTrace()
                            "downloadXML: Security Exception. Needs Permission? ${e.message}"
                        }
                        else -> "Unknown error: ${e.message}"
                    }

                    return ""   //if it gets to here theres been a problem, return empty string
                }

            }


        }
    }
}



