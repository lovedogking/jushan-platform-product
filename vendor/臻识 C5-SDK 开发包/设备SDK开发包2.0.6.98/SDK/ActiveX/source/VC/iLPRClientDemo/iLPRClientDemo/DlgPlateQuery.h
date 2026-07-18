#pragma once
#include "resource.h"
#include "afxcmn.h"
#include "vzlprclientctrlctrl1.h"

// CDlgPlateQuery dialog

class CDlgPlateQuery : public CDialog
{
	DECLARE_DYNAMIC(CDlgPlateQuery)

public:
	CDlgPlateQuery(CWnd* pParent = NULL);   // standard constructor
	virtual ~CDlgPlateQuery();

// Dialog Data
	enum { IDD = IDD_DLG_PLATE_QUERY };

protected:
	virtual void DoDataExchange(CDataExchange* pDX);    // DDX/DDV support
	afx_msg void OnBnClickedBtnUp();
	afx_msg void OnBnClickedBtnDown();
	virtual BOOL OnInitDialog();
	DECLARE_MESSAGE_MAP()

public:
	void SetDeviceIP( CString strIP, int nPort, CString strUserName, CString strPwd );
	void QueryByPage(int nPageIndex);

private:
	CListCtrl m_lstPlate;
	COleDateTime m_dtStart;
	COleDateTime m_dtStartTime;
	COleDateTime m_dtEnd;
	COleDateTime m_dtEndTime;
	CString m_strPlate;
	CString m_strPageMsg;

	CString m_strStartTime;
	CString m_strEndTime;

	int m_nTotalCount;
	int m_nCurPage;
	int m_nPageCount;

	int m_nLprHandle;
	CString m_strDeivceIP;
	int m_nPort;
	CString m_strUserName;
	CString m_strPwd;

	CVzlprclientctrlctrl1 m_lprClientCtrl;
public:
	afx_msg void OnClose();
	afx_msg void OnBnClickedBtnQuery();
	DECLARE_EVENTSINK_MAP()
	void OnLPRQueryPlateInfoOutVzlprclientctrlctrl1(LPCTSTR license, LPCTSTR color, short colorIndex, short nType, short confidence, short bright, short nDirection, long time, short carBright, short carColor, LPCTSTR imgPath, LPCTSTR ip, 
		short resultType, long plateID);
	
	void OnLPRQueryPlateInfoOutExVzlprclientctrlctrl1(LPCTSTR license, LPCTSTR color, short colorIndex, short nType, short confidence, short bright, short nDirection, long time, short carBright, short carColor, LPCTSTR imgPath, LPCTSTR ip, 
		short resultType, long plateID, LPCTSTR plateDateTime);
};
