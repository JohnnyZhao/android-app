package one.mixin.android.repository

import androidx.lifecycle.LiveData
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import one.mixin.android.api.handleMixinResponse
import one.mixin.android.api.request.CircleConversationRequest
import one.mixin.android.api.service.CircleService
import one.mixin.android.api.service.UserService
import one.mixin.android.db.AppDao
import one.mixin.android.db.CircleConversationDao
import one.mixin.android.db.CircleDao
import one.mixin.android.db.UserDao
import one.mixin.android.db.insertUpdate
import one.mixin.android.db.insertUpdateList
import one.mixin.android.db.runInTransaction
import one.mixin.android.db.updateRelationship
import one.mixin.android.util.Session
import one.mixin.android.vo.App
import one.mixin.android.vo.Circle
import one.mixin.android.vo.CircleBody
import one.mixin.android.vo.CircleConversation
import one.mixin.android.vo.CircleOrder
import one.mixin.android.vo.ConversationCircleItem
import one.mixin.android.vo.User
import one.mixin.android.vo.UserRelationship

@Singleton
class UserRepository
@Inject
constructor(
    private val userDao: UserDao,
    private val appDao: AppDao,
    private val circleDao: CircleDao,
    private val userService: UserService,
    private val circleService: CircleService,
    private val circleConversationDao: CircleConversationDao
) {

    fun findFriends(): LiveData<List<User>> = userDao.findFriends()

    suspend fun getFriends(): List<User> = userDao.getFriends()

    suspend fun fuzzySearchUser(query: String): List<User> = userDao.fuzzySearchUser(query, query, Session.getAccountId() ?: "")

    suspend fun fuzzySearchGroupUser(conversationId: String, query: String): List<User> =
        userDao.fuzzySearchGroupUser(conversationId, query, query, Session.getAccountId() ?: "")

    suspend fun suspendGetGroupParticipants(conversationId: String): List<User> =
        userDao.suspendGetGroupParticipants(conversationId, Session.getAccountId() ?: "")

    fun findUserById(query: String): LiveData<User> = userDao.findUserById(query)

    suspend fun suspendFindUserById(query: String) = userDao.suspendFindUserById(query)

    fun getUserById(id: String): User? = userDao.findUser(id)

    suspend fun findUserExist(userIds: List<String>): List<String> = userDao.findUserExist(userIds)

    fun getUser(id: String) = userService.getUserById(id)

    suspend fun getAppAndCheckUser(id: String, updatedAt: String?): App? {
        val app = findAppById(id)
        if (app?.updatedAt != null && app.updatedAt == updatedAt) {
            return app
        }

        return handleMixinResponse(
            invokeNetwork = {
                userService.getUserByIdSuspend(id)
            },
            successBlock = {
                it.data?.let { u ->
                    withContext(Dispatchers.IO) {
                        upsert(u)
                    }
                    return@handleMixinResponse u.app
                }
            }
        )
    }

    fun findUserByConversationId(conversationId: String): LiveData<User> =
        userDao.findUserByConversationId(conversationId)

    fun findContactByConversationId(conversationId: String): User? =
        userDao.findContactByConversationId(conversationId)

    suspend fun suspendFindContactByConversationId(conversationId: String): User? =
        userDao.suspendFindContactByConversationId(conversationId)

    fun findSelf(): LiveData<User?> = userDao.findSelf(Session.getAccountId() ?: "")

    suspend fun upsert(user: User) = coroutineScope {
        userDao.insertUpdate(user, appDao)
    }

    suspend fun upsertList(users: List<User>) = coroutineScope {
        userDao.insertUpdateList(users, appDao)
    }

    suspend fun insertApp(app: App) = coroutineScope {
        appDao.insert(app)
    }

    suspend fun upsertBlock(user: User) = withContext(Dispatchers.IO) {
        userDao.updateRelationship(user, UserRelationship.BLOCKING.name)
    }

    fun updatePhone(id: String, phone: String) = userDao.updatePhone(id, phone)

    suspend fun findAppById(id: String) = appDao.findAppById(id)

    suspend fun searchAppByHost(query: String) = appDao.searchAppByHost("%$query%")

    fun findContactUsers() = userDao.findContactUsers()

    suspend fun findFriendsNotBot() = userDao.findFriendsNotBot()

    fun findAppsByIds(appIds: List<String>) = appDao.findAppsByIds(appIds)

    suspend fun findMultiUsersByIds(ids: Set<String>) = userDao.findMultiUsersByIds(ids)

    suspend fun fetchUser(ids: List<String>) = userService.fetchUsers(ids)

    suspend fun findUserByIdentityNumberSuspend(identityNumber: String) = userDao.suspendFindUserByIdentityNumber(identityNumber)

    suspend fun findUserIdByAppNumber(conversationId: String, appNumber: String) = userDao.findUserIdByAppNumber(conversationId, appNumber)

    suspend fun createCircle(name: String) = circleService.createCircle(CircleBody(name))

    fun observeAllCircleItem() = circleDao.observeAllCircleItem()

    suspend fun insertCircle(circle: Circle) = circleDao.insertSuspend(circle)

    suspend fun circleRename(circleId: String, name: String) = circleService.updateCircle(circleId, CircleBody(name))

    suspend fun deleteCircle(circleId: String) = circleService.deleteCircle(circleId)

    suspend fun deleteCircleById(circleId: String) = circleDao.deleteCircleById(circleId)

    suspend fun findConversationItemByCircleId(circleId: String) =
        circleDao.findConversationItemByCircleId(circleId)

    suspend fun updateCircleConversations(id: String, circleConversationRequests: List<CircleConversationRequest>) =
        circleService.updateCircleConversations(id, circleConversationRequests)

    suspend fun sortCircleConversations(list: List<CircleOrder>?) = withContext(Dispatchers.IO) {
        runInTransaction {
            list?.forEach {
                circleDao.updateOrderAt(it.circleId, it.orderAt)
            }
        }
    }

    suspend fun getCircleById(circleId: String) = circleService.getCircleById(circleId)

    suspend fun deleteCircleConversation(conversationId: String, circleId: String) =
        circleConversationDao.deleteByIds(conversationId, circleId)

    suspend fun deleteByCircleId(circleId: String) =
        circleConversationDao.deleteByCircleId(circleId)

    suspend fun insertCircleConversation(circleConversation: CircleConversation) =
        circleConversationDao.insertSuspend(circleConversation)

    suspend fun findCircleConversationByCircleId(circleId: String) =
        circleConversationDao.findCircleConversationByCircleId(circleId)

    suspend fun getIncludeCircleItem(conversationId: String): List<ConversationCircleItem> = circleDao.getIncludeCircleItem(conversationId)

    suspend fun getOtherCircleItem(conversationId: String): List<ConversationCircleItem> = circleDao.getOtherCircleItem(conversationId)
}
