package `fun`.platonic.pulsar.ql

import `fun`.platonic.pulsar.PulsarSession
import `fun`.platonic.pulsar.common.PulsarContext.applicationContext
import `fun`.platonic.pulsar.persist.WebPage
import `fun`.platonic.pulsar.ql.annotation.UDAggregation
import `fun`.platonic.pulsar.ql.annotation.UDFGroup
import `fun`.platonic.pulsar.ql.annotation.UDFunction
import `fun`.platonic.pulsar.ql.h2.udfs.CommonFunctions
import `fun`.platonic.pulsar.ql.types.ValueDom
import com.google.common.reflect.ClassPath
import org.h2.engine.SessionInterface
import org.slf4j.LoggerFactory
import kotlin.reflect.KClass

open class QuerySession(dbSession: DbSession, config: SessionConfig): PulsarSession(applicationContext, config) {
    val LOG = LoggerFactory.getLogger(QuerySession::class.java)
    private var totalUdfs: Int = 0

    init {
        registerUdfsInPackage(dbSession.implementation as org.h2.engine.Session, "fun.platonic.nebula.ql")
    }

    fun parseToValue(page: WebPage): ValueDom {
        return ValueDom.get(parse(page))
    }

    /**
     * Register user defined functions into database
     * TODO: Hot register UDFs
     */
    fun registerUdfsInPackage(session: SessionInterface, pack: String) {
        ClassPath.from(CommonFunctions.javaClass.classLoader)
                .getTopLevelClasses(CommonFunctions.javaClass.`package`.name)
                .map { it.load() }
                .filter { it.annotations.any { it is UDFGroup } }
                .forEach { registerUdfs(session, it.kotlin) }

        if (totalUdfs > 0) {
            LOG.info("Added total {} new UDFs", totalUdfs)
        }
    }

    /**
     * Register a java UDF class
     * */
    fun registerUdfs(session: SessionInterface, udfClass: Class<out Any>) {
        return registerUdfs(session, udfClass.kotlin)
    }

    /**
     * Register a kotlin UDF class
     * */
    fun registerUdfs(session: SessionInterface, udfClass: KClass<out Any>) {
        val group = udfClass.annotations.first { it is UDFGroup } as UDFGroup
        val namespace = group.namespace

        // register shortcuts if required
        udfClass.members
                .filter { it.annotations.any { a -> a is UDFunction && a.hasShortcut } }
                .forEach { registerUdf(session, udfClass, it.name) }

        // register udfs
        udfClass.members
                .filter { it.annotations.any { a -> a is UDFunction } }
                .forEach { registerUdf(session, udfClass, it.name, namespace) }
    }

    /**
     * Register user defined aggregation functions
     * We support arbitrary "_" in a UDF name, for example, the following functions are the same:
     * some_fun_(), _____some_fun_(), some______fun()
     */
    fun registerUdaf(session: SessionInterface, udafClass: KClass<out org.h2.api.Aggregate>) {
        val group = udafClass.annotations.first { it is UDFGroup } as UDFGroup
        val aggregation = udafClass.annotations.first { it is UDAggregation } as UDAggregation
        val namespace = group.namespace
        val name = aggregation.name

        var aggregateName = if (namespace.isEmpty()) name else namespace + "_" + name
        aggregateName = aggregateName.replace("_", "").toUpperCase()

        var sql = "DROP AGGREGATE IF EXISTS $aggregateName"
        var command = session.prepareCommand(sql, Int.MAX_VALUE)
        command.executeUpdate()

        // Notice : can not use session.prepare(sql) here, which causes a call cycle
        sql = "CREATE AGGREGATE IF NOT EXISTS $aggregateName FOR \"${udafClass.qualifiedName}\""
        command = session.prepareCommand(sql, Int.MAX_VALUE)
        command.executeUpdate()

        if (LOG.isTraceEnabled) {
            LOG.trace(sql)
        }
    }

    /**
     * Register user defined functions
     * We support arbitrary "_" in a UDF name, for example, the following functions are the same:
     * some_fun_(), _____some_fun_(), some______fun()
     */
    private fun registerUdf(session: SessionInterface, udfClass: KClass<out Any>, method: String, namespace: String = "") {
        var alias = if (namespace.isEmpty()) method else namespace + "_" + method

        // All underscores are ignored
        alias = alias.replace("_", "").toUpperCase()

        var sql = "DROP ALIAS IF EXISTS $alias"
        // Notice : can not use session.prepare(sql) here, which causes a call cycle
        var command = session.prepareCommand(sql, Int.MAX_VALUE)
        command.executeUpdate()

        // Notice : can not use session.prepare(sql) here, which causes a call cycle
        sql = "CREATE ALIAS IF NOT EXISTS $alias FOR \"${udfClass.qualifiedName}.$method\""
        command = session.prepareCommand(sql, Int.MAX_VALUE)
        command.executeUpdate()

        if (LOG.isTraceEnabled) {
            LOG.trace(sql)
        }
    }
}
