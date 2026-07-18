// VZLPRClientDemoDlg.h : header file
//

#pragma once
#include "vzlprclientctrlctrl1.h"
#include "afxcmn.h"
#include <atlimage.h>
#include "afxwin.h"
#include "DlgWlist.h"

#include <vector>
#include <map>
using namespace std;

class CLoginInfo
{
public:
	CLoginInfo();
	~CLoginInfo();

	void SetLoginInfo(LPCTSTR strIP, int nPort, LPCTSTR strUserName, LPCTSTR strPwd, long lHandle );

	LPCTSTR GetIP();
	int GetPort();
	LPCTSTR GetUserName( );
	LPCTSTR GetPwd();
	long GetHandle( );

	void SetCurSize(int nCurSize);
	int GetCurSize( );

	void SetIsOffline( bool bOffline );
	bool GetIsOffline( );

	void SetIsOutputRealResult( bool bOutputRealResult);
	bool GetIsOutputRealResult( );

	void SetIsLoadOffline( bool bLoad );
	bool GetIsLoadOffline( );

private:
	char m_szIP[32];
	int m_nPort;
	char m_szUserName[32];
	char m_szPwd[32];
	long m_lHandle;

	int m_nCurSize;
	bool m_bOffline;
	bool m_bLoadOffline;
	bool m_bOutputRealResult;
};

// CVZLPRClientDemoDlg dialog
class CVZLPRClientDemoDlg : public CDialog
{
// Construction
public:
	CVZLPRClientDemoDlg(CWnd* pParent = NULL);	// standard constructor

// Dialog Data
	enum { IDD = IDD_VZLPRCLIENTDEMO_DIALOG };

	protected:
	virtual void DoDataExchange(CDataExchange* pDX);	// DDX/DDV support


// Implementation
protected:
	HICON m_hIcon;

	// Generated message map functions
	virtual BOOL OnInitDialog();
	afx_msg void OnSysCommand(UINT nID, LPARAM lParam);
	afx_msg void OnPaint();
	afx_msg HCURSOR OnQueryDragIcon();
	afx_msg LRESULT OnPlateMessage(WPARAM wParam, LPARAM lParam); 
	afx_msg LRESULT OnShowImgMessage(WPARAM wParam, LPARAM lParam); 
	afx_msg void OnTimer(UINT_PTR nIDEvent);
	afx_msg void OnClose();
	afx_msg void OnBnClickedChkOfflineData();
	DECLARE_MESSAGE_MAP()
public:
	CVzlprclientctrlctrl1 m_vzLPRClientCtrl;
	afx_msg void OnBnClickedBtnOpen();
	afx_msg void OnBnClickedBtnClose();
	afx_msg void OnBnClickedBtnPlay();
	afx_msg void OnBnClickedBtnStop();
	CIPAddressCtrl m_deviceIP;
	CString m_strPort;
	CString m_strUserName;
	CString m_strPwd;

	CListCtrl m_lstDevice;
	DECLARE_EVENTSINK_MAP()
	void OnLPRPlateInfoOutVzlprclientctrlctrl1(LPCTSTR license, LPCTSTR color, short colorIndex, short type, short confidence, short bright, short nDirection, long time, short carBright, short carColor, LPCTSTR ip, short resultType);
	void SetWindowStyleVzlprclientctrlctrl1(short nSytle);
	void OnLPRPlateImgOutVzlprclientctrlctrl1(LPCTSTR imagePath, LPCTSTR ip, short resultType);
	afx_msg void OnBnClickedChkOpen();
	CString m_strSavePath;
	BOOL m_bOpen;
	CStatic m_sPicPlate;
	CString m_strLblResult;

	CString m_strFullImgPath;
	afx_msg void OnBnClickedBtnImg();
	CComboBox m_cmbStyle;
	CString m_strWinIndex;
	afx_msg void OnBnClickedBtnDir();
	afx_msg void OnCbnSelchangeCmbStyle();

	int m_lprHandle;
	DWORD m_bkColor;
	DWORD m_borderColor;
	DWORD m_activeBorderColor;

	BOOL GetIsOpened( LPCSTR strIP );

	int GetSeleIndex( );
	afx_msg void OnBnClickedBtnPlay2();
	afx_msg void OnBnClickedBtnIoOpen();
	void OnLPRCommonNotifyVzlprclientctrlctrl1(LPCTSTR ip, short nNotify);
	void OnLPRPlateInfoOutExVzlprclientctrlctrl1(LPCTSTR license, LPCTSTR color, short colorIndex, short nType, short confidence, short bright, short nDirection, long time, short carBright, short carColor, LPCTSTR imgPath, LPCTSTR ip, 
		short resultType);
	afx_msg void OnBnClickedBtn485();
	CComboBox m_cmbBote;
	afx_msg void OnBnClickedBtnWlist();
	void OnLPRWlistInfoOutVzlprclientctrlctrl1(long lVehicleID, LPCTSTR strPlateID, BOOL bEnable, LPCTSTR strOverdule, BOOL bUsingTimeSeg, BOOL bAlarm, LPCTSTR strReserved, long lCustomerID, LPCTSTR strCustomerName, LPCTSTR strCustomerCode);

	
	afx_msg void OnBnClickedBtnSetbkcolor();
	afx_msg void OnBnClickedBtnSetbordercolor();
	afx_msg void OnBnClickedBtnSetactivebordercolor();
	afx_msg void OnMove(int x, int y);
	afx_msg void OnBnClickedBtnPlateQuery();
	BOOL m_bSetOffline;
	afx_msg void OnBnClickedChkSetOffline();
	afx_msg void OnHdnItemclickListDevice(NMHDR *pNMHDR, LRESULT *pResult);
	//	afx_msg void OnHdnItemclickListDevice(NMHDR *pNMHDR, LRESULT *pResult);
	CButton m_btnStartRecord;
	CButton m_btnStopRecord;

	vector<int> m_vecRecordHandle;
	afx_msg void OnBnClickedBtnStartRecord();
	afx_msg void OnBnClickedBtnStopRecord();
	afx_msg void OnNMClickListDevice(NMHDR *pNMHDR, LRESULT *pResult);

	void iRecordVideo(int lLPRHandle, CString strDevice );
	void iReRecordAllVideo( );

	CString iGetDeviceItemText( long lLPRClient );
	long GetItemHandle( int nIndex );
	CLoginInfo *GetCurItemInfo( );

	char m_strModuleDir[MAX_PATH];
	char m_strModuleName[MAX_PATH];
	BOOL m_bSaveOfflineData;
	void OnLPROfflinePlateInfoOutVzlprclientctrlctrl1(LPCTSTR license, LPCTSTR color, short colorIndex, short nType, short confidence, short bright, short nDirection, long time, short carBright, short carColor, LPCTSTR imgPath, LPCTSTR ip, 
		short resultType);
	void OnLPRSerialRecvDataVzlprclientctrlctrl1(long lSerialHandle, LPCTSTR recvData, LPCTSTR ip, long lHandle);
	afx_msg void OnBnClickedChkOutputRealtimeResult();
	BOOL m_bOutputRealtimeResult;
	void DbClickVzlprclientctrlctrl1();
	CComboBox m_cmbSize;
	afx_msg void OnCbnSelchangeCmbSize();
	afx_msg void OnBnClickedBtnLedCtl();
	CComboBox m_cmbProv;

	void LoadProv( long lprHandle );
	afx_msg void OnCbnSelchangeCmbProv();
	afx_msg void OnBnClickedBtnDevicenum();
	afx_msg void OnBnClickedBtnGetversion();
	afx_msg void OnCbnSelchangeCmbBote();
	afx_msg void OnBnClickedButton1();
	afx_msg void OnBnClickedButton2();
	void OnLPRFindDeviceInfoOutVzlprclientctrlctrl1(LPCTSTR szIPAddr, long usPort1, long usPort2, long SL, long SH);
	CListCtrl m_lstFindDevice;
	afx_msg void OnNMClickList1(NMHDR *pNMHDR, LRESULT *pResult);
	afx_msg void OnBnClickedChkOpen2();

	void GetSaveCurConnStatus( );

protected:
	void deleteDeviceInfo();

protected:
	BOOL m_bPlateImgOpen;
	CString m_sPlateImgPath;
	std::map<int, bool> m_oPlateImageEnableMap;
public:
	afx_msg void OnBnClickedBtnStop2();
	afx_msg void OnBnClickedBtnDir2();
	afx_msg void OnBnClickedBtnImg2();
	CComboBox m_cmbIO;
	afx_msg void OnBnClickedBtnSaveLastImg();
	afx_msg void OnBnClickedBtnStatus();
	afx_msg void OnBnClickedBtnSetPwd();
};
