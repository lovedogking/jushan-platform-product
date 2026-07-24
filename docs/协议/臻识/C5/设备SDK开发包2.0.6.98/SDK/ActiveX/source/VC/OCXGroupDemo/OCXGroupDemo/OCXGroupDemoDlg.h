// OCXGroupDemoDlg.h : 头文件
//

#pragma once
#include "vzlprclientctrlctrl1.h"
#include "afxcmn.h"
#include "afxwin.h"

#define WM_GROUP_ENTRY_MESSAGE (WM_USER+109)
#define WM_GROUP_EXIT_MESSAGE (WM_USER+110)
#define WM_GROUP_ENTRY_IMG_MSG (WM_USER+111)
#define WM_GROUP_EXIT_IMG_MSG (WM_USER+112)

typedef struct  
{
	short recordType;
	char exitIP[64];
	char exitName[64];
	char exitLicense[64];
	char exitPlateTime[64];
	char exitImgPath[260];

	char entryIP[64];
	char entryName[64];
	char entryLicense[64];
	char entryPlateTime[64];
	char entryImgPath[260];
}
LPR_GROUP_INFO;

typedef struct 
{
	long handle;
	long id;
	char sn[64];
	char plate[16];
	bool isEntry ;
	void *userdata;
}
THREAD_GROUP_PARAM;

class CLoginInfo
{
public:
	CLoginInfo();
	~CLoginInfo();

	void SetLoginInfo(LPCTSTR strIP, int nPort, LPCTSTR strUserName, LPCTSTR strPwd,long lHandle ,int nIsEntryExit);
    long GetHandle();
	int GetIsEntryExit();

private:
	char m_szIP[32];
	int	m_nPort;
	char m_szUserName[32];
	char m_szPwd[32];
	long m_lHandle;
	int m_nIsEntryExit;

};


// COCXGroupDemoDlg 对话框
class COCXGroupDemoDlg : public CDialog
{
// 构造
public:
	COCXGroupDemoDlg(CWnd* pParent = NULL);	// 标准构造函数

// 对话框数据
	enum { IDD = IDD_OCXGROUPDEMO_DIALOG };

	protected:
	virtual void DoDataExchange(CDataExchange* pDX);	// DDX/DDV 支持


// 实现
protected:
	HICON m_hIcon;

	// 生成的消息映射函数
	virtual BOOL OnInitDialog();
	afx_msg void OnSysCommand(UINT nID, LPARAM lParam);
	afx_msg void OnPaint();
	afx_msg HCURSOR OnQueryDragIcon();
	DECLARE_MESSAGE_MAP()
public:
	CVzlprclientctrlctrl1 m_vzLprCtrl;
	CVzlprclientctrlctrl1 m_vzLprVideoCtrl;

	CString m_strDeviceIP;
	CString m_strPort;
	CString m_strUserName;
	CString m_strPwd;

	CString m_strDeviceIP2;
	CString m_strPort2;
	CString m_strUserName2;
	CString m_strPwd2;

	CListCtrl m_lstDevice;
	CListCtrl m_lstGroupInfo;
    
	afx_msg void OnBnClickedBtnOpen();
	afx_msg void OnBnClickedBtnClose();

	BOOL GetIsOpened(LPCTSTR strIP);
	int GetSeleIndex();
	int GetSeleIsEntryExit(int nIndex );
	long GetItemHandle( int nIndex );
	CString GetAppPath();


	void LoadGroupImg(long handle, long id, LPCTSTR sn, LPCTSTR  plate, bool isEntry );

	int IsPathExist(const char *filename);

	afx_msg void OnBnClickedBtnVideoplay();
	afx_msg void OnBnClickedBtnVideostop();
	afx_msg void OnBnClickedBtnManual();
	afx_msg LRESULT OnShowGroupEntryMessage(WPARAM w,LPARAM l);
    afx_msg LRESULT OnShowGroupExitMessage(WPARAM w,LPARAM l);
	afx_msg LRESULT OnShowGroupEntryImgMsg(WPARAM w,LPARAM l);
	afx_msg LRESULT OnShowGroupExitImgMsg(WPARAM w,LPARAM l);

	DECLARE_EVENTSINK_MAP()
	void OnLPRPlateGrouptInfoOutVzlprclientctrlctrl1(LPCTSTR exitIP, LPCTSTR exitName, LPCTSTR exitSN, short exitDGType, LPCTSTR exitLicense, LPCTSTR exitColor, short exitPlateType, short exitConfidence, long exitPlateID, 
		LPCTSTR exitPlateTime, short exitResultType, LPCTSTR entryIP, LPCTSTR entryName, LPCTSTR entrySN, short entryDGType, LPCTSTR entryLicense, LPCTSTR entryColor, short entryPlateType, short entryConfidence, long entryPlateID, 
		LPCTSTR entryPlateTime, short entryResultType, long lprHandle, short recordType);
	
	afx_msg void OnBnClickedBtnOpen2();
	afx_msg void OnBnClickedBtnClose2();
	afx_msg void OnBnClickedBtnGroupopen();
	afx_msg void OnBnClickedBtnGroupclose();
	
	void OnLPRPlateGrouptInfoOutVzlprclientctrlctrl2(LPCTSTR exitIP, LPCTSTR exitName, LPCTSTR exitSN, short exitDGType, LPCTSTR exitLicense, LPCTSTR exitColor, short exitPlateType, short exitConfidence, long exitPlateID, 
		LPCTSTR exitPlateTime, short exitResultType, LPCTSTR entryIP, LPCTSTR entryName, LPCTSTR entrySN, short entryDGType, LPCTSTR entryLicense, LPCTSTR entryColor, short entryPlateType, short entryConfidence, long entryPlateID, 
		LPCTSTR entryPlateTime, short entryResultType, long lprHandle, short recordType);
	
	CStatic m_sEntryPlate;
	CStatic m_sExitPlate;
};
