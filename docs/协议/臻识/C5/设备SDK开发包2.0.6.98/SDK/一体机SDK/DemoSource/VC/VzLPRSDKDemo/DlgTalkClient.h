#pragma once
#include "afxcmn.h"
#include "VzTreeCtrl.h"
#include "VzDeviceLPR.h"
#include <VzLPRClientSDK.h>

#include <vector>
#include <list>
#include <string>
#include "resource.h"
#include "afxwin.h"

using namespace std;

// CDlgTalkClient 对话框

#define WM_TALK_RESULT_MESSAGE (WM_USER+195)  

class CDlgTalkClient : public CDialog
{
	DECLARE_DYNAMIC(CDlgTalkClient)

public:
	CDlgTalkClient(CWnd* pParent = NULL);   // 标准构造函数
  virtual ~CDlgTalkClient();
  void InitTreeData(vector <DeviceLPR *> *treedata);

// 对话框数据
	enum { IDD = IDD_DLG_TALK };

protected:
  bool m_bRecording;
  int m_recordTime;

	virtual void DoDataExchange(CDataExchange* pDX);    // DDX/DDV 支持
  HTREEITEM CDlgTalkClient::finditem(HTREEITEM  item, CString strtext);
  void UpdateTree();
  void AddOnPhtonText(CString &str);      // 字符串中加入“通话中”
  void AddCallingText(CString &str);      // 字符串中加入“呼叫中”
  void RemoveOnPhoneText(CString &str);   // 删除字符串中的“通话中”
  void RemoveCallingText(CString &str);   // 删除字符串中的“呼叫中”

//  int GetTalkServerStatus(CString strIP);

  int StartTalkByIP(CString strIP);
  int StartTalkByItem(HTREEITEM item);

  void StopTalkByIP(CString strIP);
  void StopTalkByItem(HTREEITEM item);
  void StopTalk();

  bool IsItemOnPhone(HTREEITEM item);     //对象正在通话
  bool IsItemCalling(HTREEITEM item);     //对象正在被呼叫

  void StartRecord();
  void StopRecord();
  void RefreshRecordBtnText(CString str);

  void StopRequestTalk();

  std::string GetAppPath();


  CImageList *m_pImageList;
  vector <DeviceLPR *> *m_rDev;
  list <CString> m_RequestIP;
	DECLARE_MESSAGE_MAP()
public:
  afx_msg void OnBnClickedBtnRecive();
  afx_msg void OnBnClickedBtnClose();
  afx_msg void OnBnClickedBtnRecord();
  afx_msg LRESULT OnReciveTalkMsg(WPARAM w,LPARAM l);
  VzTreeCtrl m_treeTalkDeviceList;
  virtual BOOL OnInitDialog();
  void RequestTalk(VzLPRClientHandle handle);

  VzLPRClientHandle m_ntalkHandle;

  CButton m_btnStartTalk;
  CButton m_btnStopTalk;
  CButton m_btnRecord;
  afx_msg void OnSelchangedTalkTreeDevice(NMHDR *pNMHDR, LRESULT *pResult);
  afx_msg void OnTimer(UINT_PTR nIDEvent);
  afx_msg void OnClose();
};
