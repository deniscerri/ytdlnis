package com.deniscerri.ytdl.core.packages

object NodeJS : PackageBase() {
    override val executableName: String get() = "node"
    override val packageFolderName: String get() = "node"
    override val bundledZipName: String get() = "libnode.zip.so"
    override val canUninstall: Boolean = true
    override val bundledVersion: String get() = ""
    override val githubRepo: String  get() = "deniscerri/ytdlnis-packages"
    override val githubPackageName: String  get() = "nodejs"
    override val apkPackage: String get() = "com.deniscerri.ytdl.nodejs"

    fun getDNSSetup() : String {
        return """

const dns = require('dns');
const net = require('net');

const SERVERS = ['8.8.8.8', '1.1.1.1'];
dns.setServers(SERVERS);

const resolver = new dns.Resolver();
resolver.setServers(SERVERS);

dns.lookup = function patchedLookup(hostname, options, callback) {
    if (typeof options === 'function') {
        callback = options;
        options = {};
    }
    options = options || {};

    const wantAll = options.all === true;
    const family = options.family || 0; // 0 = either

    // Literal IPs / wildcard bind addresses: resolve instantly, no network.
    const ipVersion = net.isIP(hostname);
    if (ipVersion) {
        if (wantAll) return callback(null, [{ address: hostname, family: ipVersion }]);
        return callback(null, hostname, ipVersion);
    }

    // localhost: resolve instantly, no network.
    if (hostname === 'localhost') {
        const results = [];
        if (family !== 6) results.push({ address: '127.0.0.1', family: 4 });
        if (family !== 4) results.push({ address: '::1', family: 6 });
        if (wantAll) return callback(null, results);
        const first = results[0];
        return callback(null, first.address, first.family);
    }

    // Real hostnames: resolve via c-ares against our explicit DNS servers.
    const tryFamily = (fam, cb) => {
        const method = fam === 6 ? 'resolve6' : 'resolve4';
        resolver[method](hostname, (err, addresses) => {
            if (err) return cb(err);
            cb(null, addresses.map((address) => ({ address, family: fam })));
        });
    };

    const finish = (err, results) => {
        if (err) return callback(err);
        if (wantAll) return callback(null, results);
        const first = results[0];
        callback(null, first.address, first.family);
    };

    if (family === 4 || family === 6) {
        return tryFamily(family, (err, results) => finish(err, results));
    }

    // Try IPv4 first, fall back to IPv6 if that fails.
    tryFamily(4, (err4, results4) => {
        if (!err4) return finish(null, results4);
        tryFamily(6, (err6, results6) => {
            if (!err6) return finish(null, results6);
            finish(err4); // report the original (v4) error
        });
    });
};
            
        """.trimIndent()
    }
}