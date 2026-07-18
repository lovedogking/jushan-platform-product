/**************************************************************************
* Copyright ©, 2008-2017, Vision-Zenith Technology Co., Ltd.
* @le:   posix_thread.h
* @Author: icekylin
* @Date:   2017-10-18
* @brief:
*
***************************************************************************/
#ifndef SRC_LIB_COMMON_POSIX_THREAD_H_
#define SRC_LIB_COMMON_POSIX_THREAD_H_

#ifndef WIN32
#include <pthread.h>
#include <sys/time.h>
#endif

namespace vzsdk {

class Mutex {
  public:
    friend class Cond;
    Mutex();
    ~Mutex();
    void Lock();
    void UnLock();

  protected:
    pthread_mutex_t *get_mutex();
  private:
    pthread_mutex_t mutex_;

};

class ThreadMutex {
  public:
    friend class Cond;
    ThreadMutex();
    ~ThreadMutex();
    void Lock();
    void UnLock();
    unsigned long  GetBelongThreadId();
  protected:
    pthread_mutex_t *get_mutex();
  private:
    pthread_mutex_t mutex_;
    unsigned long lock_threadid_;
};

template <class _Mtx>
class ThreadLockGuard {
  public:
    explicit ThreadLockGuard(_Mtx *mutex) : mutex_(mutex), lock_flag_(false) {
        unsigned long threadid = pthread_self();
        unsigned long mutexthreadid = mutex->GetBelongThreadId();
        if ( (mutexthreadid == 0) || (threadid != mutexthreadid)) {
            mutex_->Lock();
            lock_flag_ = true;
        }

    }

    ~ThreadLockGuard() {
        if (lock_flag_) {
            mutex_->UnLock();

        }
    }


  private:
    _Mtx *mutex_;

    bool   lock_flag_;
};

class NullMutex {
  public:
    NullMutex() { }
    void Lock() {}
    void UnLock() {}
};

class Cond {
  public:
    Cond();
    ~Cond();

    void Wait(Mutex *mutex);
    void TimeWait(Mutex *mutex, struct timespec *tv);
    void Signal();
    void Broadcast();
  private:
    pthread_cond_t vz_cond_;
};

template <class _Mtx>
class LockGuard {
  public:
    explicit LockGuard(_Mtx *mutex) : mutex_(mutex) {
        mutex_->Lock();
    }

    ~LockGuard() {
        mutex_->UnLock();
    }

  private:
    _Mtx *mutex_;
};

class WaitCond {
  public:
    void Wait(long sec); // NOLINT
    void WakeUp();

  private:
    Cond cond_;
    Mutex mutex_;
};

typedef void *(*start_routine)(void *);
class PosixThread {
  public:
    bool Create(start_routine routine, void *arg);
    void Stop();
  protected:
    struct ThreadCtx {
        start_routine routine_;
        void *arg_;
        pthread_t self_;
    };
  private:
    ThreadCtx thread_ctx_;
};

class BasicThread {
  public:
    BasicThread();
    virtual ~BasicThread();
    virtual bool Start();
    virtual void Stop();
    virtual bool IsRun();

  protected:
    virtual void Exit();
    virtual void Run() = 0;
    bool is_run_;

  private:
    static void *Run(void *);

  protected:
    PosixThread thread_;
    WaitCond  cond_;
};

struct SimpleThread {
    static void run_once(start_routine routine, void *arg);
};


}  // namespace vzsdk

#endif  // SRC_LIB_COMMON_POSIX_THREAD_H_
