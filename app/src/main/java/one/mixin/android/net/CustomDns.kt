package one.mixin.android.net

import okhttp3.Dns
import org.xbill.DNS.ARecord
import org.xbill.DNS.Lookup
import org.xbill.DNS.Record
import org.xbill.DNS.Resolver
import org.xbill.DNS.SimpleResolver
import org.xbill.DNS.TextParseException
import org.xbill.DNS.Type
import timber.log.Timber
import java.net.InetAddress
import java.net.UnknownHostException

class CustomDns(private val dnsHostname: String) : Dns {

    override fun lookup(hostname: String): List<InetAddress> {
        val resolver: Resolver = SimpleResolver(dnsHostname)
        val lookup: Lookup = doLookup(hostname)
        lookup.setResolver(resolver)
        val records: Array<Record> = lookup.run()
        val ipv4Addresses = records.filter { it.type == Type.A }
            .map { r ->
                r as ARecord
            }.map {
                val kFunction = ARecord::getAddress
                kFunction(it)
            }
        if (ipv4Addresses.isNotEmpty()) {
            return ipv4Addresses
        }
        throw UnknownHostException(hostname)
    }

    companion object {
        @Throws(UnknownHostException::class)
        private fun doLookup(hostname: String?): Lookup {
            return try {
                Lookup(hostname)
            } catch (e: TextParseException) {
                throw UnknownHostException()
            }
        }
    }
}