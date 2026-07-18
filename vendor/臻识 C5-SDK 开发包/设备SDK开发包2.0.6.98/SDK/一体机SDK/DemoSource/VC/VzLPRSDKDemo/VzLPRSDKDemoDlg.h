// VzLPRSDKDemoDlg.h : 头文件
//

#pragma once
#include<VzLPRClientSDK.h>
#include <VzLPRFaceClientSDK.h>

#include "VzDeviceLPR.h"
#include "InteractViewer.h"
#include "VzTreeCtrl.h"
#include <vector>
#include "DlgTriggerShow.h"
#include "GDIDraw.h"
#include "afxwin.h"
#include <atltime.h>
#include "afxcmn.h"

using namespace std;

#define MAX_PLATE_LENGTH  16
#define MAX_OUTPUT_NUM	4
#define MAX_LEN_STR		256
#define MAX_SERIAL_RECV_SIZE	256

#define WM_USER_MSG	(WM_USER+200)
#define WM_PLATE_INFO_MSG (WM_USER+201)
#define WM_SHOWMSG (WM_USER + 202)

class OutputWin
{
public:
	OutputWin()
		: pEditResult(NULL), m_bEnableShow(false)
		, m_hClient(0), m_bEnableFrame(false)
		, m_pDraw(NULL), nPlayHandle(0)
	{
		InitializeCriticalSection(&m_csFrame);
		m_strResult[0] = 0;
	}
	~OutputWin()
	{
		DeleteCriticalSection(&m_csFrame);
		if(m_pDraw)
			delete m_pDraw;
	}

	void SetClientHandle(int hClient);
	int GetClientHandle();

	void UpdateString(const char *pStr);

	void SetFrame(const unsigned char *pFrame, unsigned uWidth, unsigned uHeight, unsigned uPitch);

	void AddPatch(const unsigned char *pPatch, unsigned uWidth, unsigned uHeight, unsigned uPitch,
		unsigned uX, unsigned uY);

	unsigned char *GetFrame(unsigned &uWidth, unsigned &uHeight, unsigned &uPitch);

	void ShowString();

	void ShowFrame();

	HWND GetHWnd();

private:
	bool iGetDraw();

public:
	InteractViewer m_struInterV;
	CEdit *pEditResult;

	int nPlayHandle;

private:
	bool m_bEnableShow;
	int m_hClient;

	bool m_bEnableFrame;
	_FRAME_BUFFER m_bufFrame;

	CRITICAL_SECTION m_csFrame;

	int m_nCDNoString;
	char m_strResult[MAX_LEN_STR];

	GDIDraw *m_pDraw;
};

#define LICENSE_LEN	16
typedef struct
{
	char strLicense[LICENSE_LEN];
	unsigned uCarID;
}
CarInfo;

// CVzLPRSDKDemoDlg 对话框
class CVzLPRSDKDemoDlg : public CDialog
{
// 构造
public:
	CVzLPRSDKDemoDlg(CWnd* pParent = NULL);	// 标准构造函数

	void OnCommonNotify0(VzLPRClientHandle handle,
		VZ_LPRC_COMMON_NOTIFY eNotify, const char *pStrDetail);

	void OnPlateInfo0(VzLPRClientHandle handle,
		const TH_PlateResult *pResult, unsigned uNumPlates,
		VZ_LPRC_RESULT_TYPE eResultType,
		const VZ_LPRC_IMAGE_INFO *pImgFull,
		const VZ_LPRC_IMAGE_INFO *pImgPlateClip);

	void OnVideoFrame0(VzLPRClientHandle handle, WORD wVideoID, const VzYUV420P *pFrame);

	void OnViewerMouse0(IV_EVENT eEvent, int x, int y, int nId);

	void OnDevFound(const char *pStrDevName, const char *pStrIPAddr, WORD usPort1, WORD usPort2, unsigned int SL, unsigned int SH, char* netmask, char* gateway);

	void OnSerialRecvData1(int nSerialHandle, const unsigned char *pRecvData, unsigned uRecvSize);

	void OnRecvCommonResult(VzLPRClientHandle handle, int type, const char *pResultInfo, int len, VZ_IMAGE_INFO *imgs, int count);

    //void SetNotifyCommStr(int exitEntryInfo,const TH_PlateResult *exitIvsInfo,const TH_PlateResult *entryIvsInfo);

// 对话框数据
	enum { IDD = IDD_VZLPRSDKDEMO_DIALOG };

	char *GetModuleDir( );

	protected:
	virtual void DoDataExchange(CDataExchange* pDX);	// DDX/DDV 支持



private:
	OutputWin *iGetOutputWinByIdx(int nIdx);
	OutputWin *iGetStableOutputWinByIdx(int nIdx);

	int iGetOutputWinIndex(VzLPRClientHandle hLPRClient = NULL);
	void iChangeDlgImageItem(VzLPRClientHandle handle, int imageID);
	
	DeviceLPR *iGetLocalDev(VzLPRClientHandle hLPRClient);

	void iCleanOutputWin(int nIdx);

	void iSaveJPEG(const VZ_LPRC_IMAGE_INFO *pImgInfo, VZ_LPRC_RESULT_TYPE eType, const char *pStrLicense, const TH_PlateResult *pResult, std::string sn);

	static void CALLBACK iOnIVPaint(int nID, bool bActive, bool bInUse, void *pUserData);
	void iOnIVPaint1(int nID, bool bActive, bool bInUse);

	void iLEDCtrl(VZ_LED_CTRL eLEDCtrl);

	void iReRecordAllVideo( );		// 重新录像

	void iRecordVideo(VzLPRClientHandle hLPRClient, CString strDevice );

	CString iGetDeviceItemText( VzLPRClientHandle hLPRClient );

    void iSetCommonInfo(const char *pStrInfo);

	void iDeleteDeviceInfo( );

	void addMsg(const string&);

	void EnableOperButton(int type);

public:
	int nForceNum;
	int nRecvNum;
	//CTime oTime;
	DWORD nTickCount;
// 实现
protected:
	
	HICON m_hIcon;

	CImageList *m_pImageList;

	OutputWin m_winOut[MAX_OUTPUT_NUM];

	int m_nIdxWinCurrSelected;

	vector <DeviceLPR *> m_vDev;

	char m_strModuleDir[MAX_PATH];
	char m_strModuleName[MAX_PATH];

	VzTreeCtrl m_treeDeviceList;

	DlgTriggerShow m_dlgTriggerShow;

	bool m_bSaveJPG;

	//用于临时存储回调中的字符串，避免回调中直接使用界面控件
	int m_nCDCleanCommNotify;
	string m_strCommNotify;
	char m_strResult[MAX_LEN_STR];
	int m_nLastSerialHandle;

	 
	CComboBox m_cmbSize;

	vector<int> m_vecRecordHandle;

	FILE *m_pFSaveLP;

	string m_sServialData;
	bool cloud_setup_;

	CString cloud_url_;
	CString cloud_user_id_;
	CString cloud_user_pwd_;

	unsigned char *m_pBufRGB;
	unsigned m_uSizeBufRGB;

	// 生成的消息映射函数
	virtual BOOL OnInitDialog();
	afx_msg void OnPaint();
	afx_msg HCURSOR OnQueryDragIcon();
	DECLARE_MESSAGE_MAP()
public:
	afx_msg void OnBnClickedBtnOpen();
	afx_msg void OnTimer(UINT_PTR nIDEvent);
	afx_msg void OnBnClickedBtnSetOutput();
	afx_msg void OnBnClickedBtnStop();
	virtual BOOL DestroyWindow();
	afx_msg void OnBnClickedBtnClose();
	afx_msg void OnBnClickedBtnSetVloop();
	afx_msg void OnBnClickedBtnSwTrigger();
	virtual BOOL PreTranslateMessage(MSG* pMsg);
	afx_msg void OnBnClickedBtnStartFind();
	afx_msg void OnBnClickedBtnStopFind();
	afx_msg void OnBnClickedChkSaveStable();
	afx_msg void OnBnClickedBtnSnapShoot();
	afx_msg void OnBnClickedChkOut1();
	afx_msg void OnCbnSelchangeCmbOutPortId();
	afx_msg void OnTvnSelchangedTreeDevFound(NMHDR *pNMHDR, LRESULT *pResult);
	afx_msg void OnBnClickedBtnConfigWlist();
	afx_msg void OnBnClickedBtnSerialRecvClean();
	afx_msg void OnBnClickedBtnSetupExt();
	afx_msg void OnTvnSelchangedTreeDevice(NMHDR *pNMHDR, LRESULT *pResult);
	afx_msg LRESULT OnUserMsg(WPARAM w,LPARAM l);
	afx_msg LRESULT OnPlateInfoMsg(WPARAM w,LPARAM l);
	afx_msg void OnBnClickedBtnStartRecord();
	afx_msg void OnBnClickedBtnStopRecord();
	afx_msg void OnBnClickedBtnOsd();
	afx_msg void OnBnClickedBtnQuery();
	CButton m_btnStartRecord;
	CButton m_btnStopRecord;
	afx_msg void OnBnClickedChkSetOffline();
	BOOL m_bSetOffline;
    afx_msg void OnBnClickedBtnGetInput();
	BOOL m_bChkOfflineData;
	afx_msg void OnBnClickedChkOfflineData();
	afx_msg void OnBnClickedBtnUpdate();
	afx_msg void OnBnClickedBtnOutput();
	afx_msg void OnBnClickedBtnNetwork();
	afx_msg void OnBnClickedBtnVoice();
	afx_msg void OnBnClickedBtnModifyIp();
	afx_msg void OnBnClickedBtnSnapImage();
	afx_msg LRESULT OnShowMsg(WPARAM w,LPARAM l);
	BOOL m_bChkOutputRealResult;
    afx_msg void OnEnChangeEditNotify();
	afx_msg void OnBnClickedBtnEncrypt();
	afx_msg void OnBnClickedBtnGroupCfg();
	afx_msg void OnBnClickedChkOutputRealresult();
	afx_msg void OnBnClickedBtnVideoCfg();
	afx_msg void OnBnClickedBtnBaseCfg();	
	afx_msg void OnBnClickedBtnOutin();
	afx_msg void OnEnChangeEditIp();
	afx_msg void OnBnClickedButton5();
	CListCtrl m_ListDevice;
	afx_msg void OnNMClickList2(NMHDR *pNMHDR, LRESULT *pResult);
	afx_msg void OnBnClickedBtnStore();

	DeviceLPR* GetCurDeviceInfo( );
	afx_msg void OnCbnSelchangeCmbSize();
	afx_msg void OnBnClickedBtnTts();
	afx_msg void OnBnClickedBtnActiveStatus();
	afx_msg void OnBnClickedBtnSerialData();
	afx_msg void OnBnClickedBtnTcpTran();
	afx_msg void OnBnClickedBtnDisconnRecord();
	afx_msg void OnBnClickedBtnTalkVoice();
	afx_msg void OnBnClickedBtnUserOsd();
	CComboBox m_cmbType;
	afx_msg void OnCbnSelchangeLoginType();
	afx_msg void OnClose();
	afx_msg void OnBnClickedBtnStartTalk();
	afx_msg void OnBnClickedBtnStopTalk();
	afx_msg void OnBnClickedBtnCloudVod();
	afx_msg void OnBnClickedBtnDevMate();
};
