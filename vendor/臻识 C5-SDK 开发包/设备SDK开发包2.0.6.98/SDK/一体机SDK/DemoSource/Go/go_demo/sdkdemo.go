package main

/*
#cgo LDFLAGS: -ldl
#cgo CFLAGS: -I callback

#include "load_sdk.h"
int plate_result_callback(long, void *, char* , void *, void *);
*/
import "C"
import "fmt"
import "unsafe"
import "time"

func main() {
   ret := C.sdk_client_setup()
   fmt.Println("ret:", ret) 

   ip := C.CString("192.168.13.209")
   user_pwd := C.CString("admin")

   handle := C.sdk_client_open(ip, 80, user_pwd, user_pwd)
   fmt.Println("handle:", handle);

   var a C.int
   udata := unsafe.Pointer(&a)

   C.sdk_client_set_plate_info_callback(handle, C.RESULT_CALLBACK(C.plate_result_callback), udata, 1)

   trigger_ret := C.sdk_client_force_trigger(handle)
   fmt.Println("trigger ret:", trigger_ret) 

   time.Sleep(2 * time.Second) 

   C.sdk_client_close(handle)
   C.sdk_client_cleanup();
}

//export plate_result_callback
func plate_result_callback(handle C.long, pUserData unsafe.Pointer,  plate *C.char, pImgFull unsafe.Pointer,  pImgPlateClip  unsafe.Pointer) C.int{
   str := C.GoString(plate)
   fmt.Println("plate:", str)
   return 0;
}