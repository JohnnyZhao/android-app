package one.mixin.android.db

import one.mixin.android.session.Session
import one.mixin.android.vo.App
import one.mixin.android.vo.Circle
import one.mixin.android.vo.CircleConversation
import one.mixin.android.vo.Message
import one.mixin.android.vo.Sticker
import one.mixin.android.vo.User

fun UserDao.insertUpdate(
    user: User,
    appDao: AppDao
) {
    runInTransaction {
        if (user.app != null) {
            user.appId = user.app!!.appId
            appDao.insert(user.app!!)
        }
        val u = findUser(user.userId)
        if (u == null) {
            insert(user)
        } else {
            update(user)
        }
    }
}

fun UserDao.insertUpdateList(
    users: List<User>,
    appDao: AppDao
) {
    runInTransaction {
        val apps = arrayListOf<App>()
        for (u in users) {
            if (u.app != null) {
                u.appId = u.app!!.appId
                apps.add(u.app!!)
            }
        }
        appDao.insertList(apps)
        insertList(users)
    }
}

fun UserDao.updateRelationship(
    user: User,
    relationship: String
) {
    runInTransaction {
        val u = findUser(user.userId)
        if (u == null) {
            insert(user)
        } else {
            user.relationship = relationship
            update(user)
        }
    }
}

fun StickerDao.insertUpdate(s: Sticker) {
    runInTransaction {
        val sticker = getStickerByUnique(s.stickerId)
        if (sticker != null) {
            s.lastUseAt = sticker.lastUseAt
        }
        if (s.createdAt == "") {
            s.createdAt = System.currentTimeMillis().toString()
        }
        insert(s)
    }
}

fun CircleConversationDao.insertUpdate(
    circleConversation: CircleConversation
) {
    runInTransaction {
        val c = findCircleConversationByCircleId(circleConversation.circleId, circleConversation.conversationId)
        if (c == null) {
            insert(circleConversation)
        } else {
            updateCheckPin(c, circleConversation)
        }
    }
}

fun CircleConversationDao.updateCheckPin(
    oldCircleConversation: CircleConversation,
    newCircleConversation: CircleConversation
) {
    if (oldCircleConversation.pinTime != null) {
        update(
            CircleConversation(
                newCircleConversation.conversationId,
                newCircleConversation.circleId,
                newCircleConversation.userId,
                newCircleConversation.createdAt,
                oldCircleConversation.pinTime
            )
        )
    } else {
        update(newCircleConversation)
    }
}

suspend fun CircleDao.insertUpdateSuspend(
    circle: Circle
) {
    withTransaction {
        val c = findCircleById(circle.circleId)
        if (c == null) {
            insert(circle)
        } else {
            update(circle)
        }
    }
}

fun CircleDao.insertUpdate(
    circle: Circle
) {
    runInTransaction {
        val c = findCircleById(circle.circleId)
        if (c == null) {
            insert(circle)
        } else {
            update(circle)
        }
    }
}

fun MixinDatabase.clearParticipant(
    conversationId: String,
    participantId: String
) {
    runInTransaction {
        participantDao().deleteById(conversationId, participantId)
        participantSessionDao().deleteByUserId(conversationId, participantId)
        participantSessionDao().emptyStatusByConversationId(conversationId)
    }
}

suspend fun MessageDao.batchMarkReadAndTake(
    conversationId: String,
    userId: String,
    createdAt: String
) {
    withTransaction {
        batchMarkRead(conversationId, userId, createdAt)
        updateConversationUnseen(userId, conversationId)
    }
}

fun MixinDatabase.deleteMessage(id: String) {
    runInTransaction {
        messageDao().deleteMessage(id)
        mentionMessageDao().deleteMessage(id)
        messageFts4Dao().deleteByMessageId(id)
    }
}

fun MixinDatabase.insertAndNotifyConversation(message: Message) {
    runInTransaction {
        messageDao().insert(message)
        val userId = Session.getAccountId()
        if (userId != message.userId) {
            conversationDao().unseenMessageCount(message.conversationId, userId)
        }
    }
}

fun MixinDatabase.findFullNameById(userId: String): String? =
    userDao().findFullNameById(userId)
