package com.nexcom.NXCore

import android.content.Context
import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.fuel.core.requests.write
import com.github.kittinunf.fuel.httpGet
import com.nexcom.nexcomnetworking.NXCore.NXNetworkOptions
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.uiThread

/**
 * Created by danielmeachum on 12/21/17.
 */


/**
  * The base class for formatting requests into a Nexcom URI string and receiving its response in primitive form.
  * Use this to perform inline requests. Otherwise, subclass NXDataManager.
  * Set the isDebug property to true for observing the created URL string and its response.
  *
  * @param rpc          The remote procedure to be called on the server. Optional as not all aspx pages require an RPC.
  * @param parameters   List of key/value pairs to be passed into the RPC.
  * @param method       Specify http method get/post. Currently only get is supported but this will be updated soon.
  *
  * @author Daniel Meachum
  *
  * @type {NXNetworkRequest}
  * @see NXDataManager
  *
*/
open class NXNetworkRequest(val options: NXNetworkOptions)
{
    constructor(rpc : String?, parameters: List<Pair<String, String>>? = null, method : String = "get") : this(NXNetworkOptions(rpc, parameters, method))

    var isDebug : Boolean
    get() {return options.isDebug}
    set(value) {options.isDebug = value}

    /**
     * Sends network request to server. Allows for inline completion and error handling.
     * Note: completion handler returns the raw String value of the response.
     * Passing in a network network allows inline customization of the server, sitetoken, sessionid, etc. If left null (as default), the default network will be used.
     * In addition to parameters passed in constructor, network network parameters such as sitetoken, sessionid, rpc, are added into the list of parameters in request.
     *
     * @param withNetwork         Specific network network to use. If null, NXNetwork.evolveJsonManager is used.
     * @param completionHandler   If request returns a response, completionHandler is called with response as String value.
     * @param errorHandler        Allows for handling of networking errors.
     */
    open fun send(completionHandler : (String)->Unit, errorHandler : (FuelError)->Unit) {

        doAsync {

            var manager = options.network
            if (manager == null) {
                manager = NXNetwork.defaultNetwork
            }

            val initialParameters = options.parameters ?: listOf()

            var allParameters = initialParameters.toMutableList()

            if (options.rpc != null) {
                allParameters.add(Pair("rpc",options.rpc!!))
            }

            var environment= manager.nexcomEnvironment

            if (environment!= null) {

                allParameters.addAll(listOf("sitetoken" to environment.sitetoken, "sessionid" to environment.sessionid))
            }

            val urlString = manager.urlString

            urlString.httpGet(allParameters).responseString { _, response, result ->

                if (isDebug) {

                    println("URL Request: " + urlString)
                    println("Response: " + response.toString())
                }


                val (json, error) = result

                if (json != null) {

                    uiThread {
                        completionHandler(json)
                    }
                }
                else if (error != null) {

                    println("Error getting json " + error)

                    uiThread {
                        errorHandler(error)
                    }
                }
            }
        }
    }

    /**
     * Sends network request to server. Allows for inline completion and error handling.
     * Note: completion handler returns the a raw ByteArray of the response.
     * Passing in a network network allows inline customization of the server, sitetoken, sessionid, etc. If left null (as default), the default network will be used.
     * In addition to parameters passed in constructor, network network parameters such as sitetoken, sessionid, rpc, are added into the list of parameters in request.
     *
     * @param withNetwork         Specific network network to use. If null, NXNetwork.evolveJsonManager is used.
     * @param completionHandler   If request returns a response, completionHandler is called with response as a ByteArray. Use when downloading image or PDF data from server.
     * @param errorHandler        Allows for handling of networking errors.
     */
    open fun sendWithDataResponse(dataCompletionHandler : (ByteArray)->Unit, errorHandler : (FuelError)->Unit) {

        doAsync {

            var manager = options.network
            if (manager == null) {
                manager = NXNetwork.defaultNetwork
            }

            val initialParameters = options.parameters ?: listOf()

            var allParameters = initialParameters.toMutableList()

            if (options.rpc != null) {
                allParameters.add(Pair("rpc",options.rpc!!))
            }

            if (options.isDebug) {
                allParameters.add("debug" to "1")
            }

            options.debugDescription?.let {
                allParameters.add("debugDesc" to it)
            }

            var environment= manager.nexcomEnvironment

            if (environment!= null) {

                allParameters.addAll(listOf("sitetoken" to environment.sitetoken, "sessionid" to environment.sessionid))
            }

            val urlString = manager.urlString

            urlString.httpGet(allParameters).response { _, response, result ->

                if (isDebug) {

                    println("URL Request: " + urlString)
                    println("Response: " + response.toString())
                }


                val (byteArray, error) = result

                if (byteArray != null) {

                    uiThread {
                        dataCompletionHandler(byteArray)
                    }
                }
                else if (error != null) {

                    println("Error getting byteArray " + error)

                    uiThread {
                        errorHandler(error)
                    }
                }
            }
        }
    }
}

/**
 * Abstract base class for essentials of a Nexcom server envrionment.
 * Used as a property when creating a network network.
 *
 * @param sitetoken   Point to a particular database.
 * @param sessionid   User's unique session identifier.
 *
 * @type {NXNetworkEnvironment}
 * @see NXNetwork
 */
data class NXNexcomEnvironment(val sitetoken : String, val sessionid : String) : NXJsonEncodable
{

}

data class NXUri(val scheme : String = "http", val host : String, val path : String) : NXJsonEncodable
{
    val urlString : String
    get() = scheme + host + path
}

/**
 * Provides specifics to identify a resource file on a server.
 * Evolve json file is preset and defaulted to.
 *
 * @param scheme    URI HTTP scheme to use. Defaults to http as Nexcom servers suck.
 * @param host      URI part host name.
 * @param path      URI part path to resource file.
 * @param nexcomEnvironment Provides an extension data class to specify nexcom environment specifics.
 */
open class NXNetwork(open var uri : NXUri, open var nexcomEnvironment: NXNexcomEnvironment? = null) : NXJsonEncodable
{

    companion object {

        /**
         * Default json resource file for shared Evolve projects.
         * Note: specific Evolve environments should be configured in individual projects.
         * @type {NXNetwork}
         */
        var defaultNetwork = NXNetwork(NXUri("http://", "evolve.nexcomgroup.com", "/apps/demo/iOS/aspx/dataAdapter.aspx"))

        private var cachedNetworks = mutableMapOf<String,NXNetwork>()

        fun open(context: Context, key: String): NXNetwork? {

            if (cachedNetworks.contains(key)) {

                return cachedNetworks[key]
            }

            val filename = key + ".network"

            if (context.fileList().contains(filename)) {

                return context.openFileInput(filename).bufferedReader().use { fromJson(it.readText()) }
            }

            return null
        }

        fun save(context: Context, key: String, network : NXNetwork) {

            val filename = key + ".network"

            context.openFileOutput(filename, Context.MODE_PRIVATE).write(network.toJson())

            cachedNetworks[key] = network
        }
    }

    /**
     * Gets the compiled url string scheme + host + path.
     * @type {String}
     */
    val urlString : String
        get() = uri.urlString
}
