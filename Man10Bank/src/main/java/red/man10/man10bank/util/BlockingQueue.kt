package red.man10.man10bank.util

import org.bukkit.Bukkit
import java.util.concurrent.LinkedBlockingQueue

object BlockingQueue {

    private var thread: Thread= Thread{ blockingQueue() }
    private val queue = LinkedBlockingQueue<()->Unit>()

    /**
     * ブロッキングキューの起動をします
     */
    fun start(){

        if (thread.isAlive){
            thread.interrupt()
        }

        thread = Thread{ blockingQueue() }

        thread.start()
    }

    /**
     * ブロッキングキューのスレッドを中断させます。　
     */
    fun stop(){
        thread.interrupt()
    }

    /**
     * 複数のスレッドでの同時動作を避けるために、スレッドはなるべくこれを使う
     */
    fun addTask(task:()->Unit){
        queue.add(task)
    }

    private fun blockingQueue(){

        Bukkit.getLogger().info("ブロッキングキューを起動(ThreadID:${thread.id})")

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