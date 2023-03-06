package red.man10.man10bank.util

import org.bukkit.Bukkit
import java.util.concurrent.LinkedBlockingQueue

object BlockingQueue {

    private lateinit var thread: Thread
    private val queue = LinkedBlockingQueue<()->Unit>()

    fun start(){

        if (thread.isAlive){
            thread.interrupt()
        }

        thread = Thread{ blockingQueue() }

        thread.start()
    }

    fun stop(){
        thread.interrupt()
    }

    /**
     * スレッドはなるべくこれを使う
     */
    fun addTask(task:()->Unit){
        queue.add(task)
    }

    private fun blockingQueue(){

        Bukkit.getLogger().info("ブロッキングキューを起動")

        while (true){

            try {
                val task = queue.take()
                task.invoke()
            }catch (e:InterruptedException){
                Bukkit.getLogger().info("ブロッキングキュー終了")
                return
            }catch (e:Exception){
                continue
            }
        }

    }


}