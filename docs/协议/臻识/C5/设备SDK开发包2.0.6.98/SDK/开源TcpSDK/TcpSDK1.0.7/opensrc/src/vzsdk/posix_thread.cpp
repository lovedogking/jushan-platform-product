/************************************************************************** 
* Copyright ©, 2008-2017, Vision-Zenith Technology Co., Ltd.
* @le:   posix_thread.cpp
* @Author: icekylin
* @Date:   2017-10-18
* @brief: 
*          
***************************************************************************/

#include "posix_thread.h"
#include <stdio.h>
#include <errno.h>

namespace vzsdk {

Mutex::Mutex()  {
  pthread_mutex_init(&mutex_, NULL);
}
Mutex::~Mutex() {
  pthread_mutex_destroy(&mutex_);
}
void Mutex::Lock() {
  pthread_mutex_lock(&mutex_);
}

void Mutex::UnLock() {
  pthread_mutex_unlock(&mutex_);
}

pthread_mutex_t *Mutex::get_mutex() {
  return &mutex_;
}


ThreadMutex::ThreadMutex() :lock_threadid_(0){
	pthread_mutex_init(&mutex_, NULL);
}
ThreadMutex::~ThreadMutex() {
	pthread_mutex_destroy(&mutex_);
}
void ThreadMutex::Lock() {
	pthread_mutex_lock(&mutex_);
	lock_threadid_ = pthread_self();
}

void ThreadMutex::UnLock() {
	lock_threadid_ = 0;
	pthread_mutex_unlock(&mutex_);
 
}

unsigned long  ThreadMutex::GetBelongThreadId()
{ 
	return lock_threadid_;
}

pthread_mutex_t *ThreadMutex::get_mutex() {
	return &mutex_;
}
 

Cond::Cond() {
  pthread_cond_init(&vz_cond_, NULL);
}

Cond::~Cond() {
	 
  pthread_cond_destroy(&vz_cond_);
 
}

void Cond::Wait(Mutex *mutex) {
  pthread_cond_wait(&vz_cond_, mutex->get_mutex());
}

void Cond::TimeWait(Mutex *mutex, struct timespec *tv) {
  pthread_cond_timedwait(&vz_cond_, mutex->get_mutex(), tv);
}

void Cond::Signal() {
  pthread_cond_signal(&vz_cond_);
}

void Cond::Broadcast() {
  pthread_cond_broadcast(&vz_cond_);
}

bool PosixThread::Create(start_routine routine, void *arg) {
  thread_ctx_.routine_ = routine;
  thread_ctx_.arg_ = arg;
  if (0 != pthread_create(&thread_ctx_.self_, NULL,
    thread_ctx_.routine_, thread_ctx_.arg_)) {
    perror("create thread failed.");
	return false;
  } 
  return true;
}

void PosixThread::Stop() {
  pthread_join(thread_ctx_.self_, NULL);
}

BasicThread::BasicThread() : is_run_(false) {
	printf("BasicThread::BasicThread \n");
}

BasicThread::~BasicThread() {
}

bool BasicThread::Start() {
	if (is_run_)
	{
		return false;
	}

	if (!thread_.Create(Run, this))
	{
		return false;
	}

      cond_.Wait(2);
	 return true;
}

void BasicThread::Stop() {
  is_run_ = false;
  Exit();
  thread_.Stop();
}

bool BasicThread::IsRun() {
  return is_run_;
}

void BasicThread::Exit() {
}

void *BasicThread::Run(void *arg) {
  BasicThread *thread = reinterpret_cast<BasicThread *>(arg);
  	thread->is_run_ = true;
  thread->cond_.WakeUp();
  while (thread->is_run_) {
    thread->Run();
  }
  return NULL;
}

void SimpleThread::run_once(start_routine routine, void *arg) {
  PosixThread thread;
  thread.Create(routine, arg);
}

void WaitCond::Wait(long sec) { // NOLINT
  LockGuard<Mutex> lock(&mutex_);

  struct timespec ts;
#ifdef WIN32
  ts.tv_sec = sec;
  ts.tv_nsec = 0;
#else
  struct timeval tv;
  gettimeofday(&tv, NULL);
  ts.tv_sec = tv.tv_sec + sec;
  ts.tv_nsec = tv.tv_usec * 1000;
#endif
  cond_.TimeWait(&mutex_, &ts);
}

void WaitCond::WakeUp() {
  cond_.Signal();
}

}  // namespace common
