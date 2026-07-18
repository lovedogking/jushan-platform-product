#pragma once
#include "afxcmn.h"
#include<VzLPRClientSDK.h>
#include <VzLPRFaceClientSDK.h>
#include "VzLPRSDKDemoDlg.h"

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
    static void CALLBACK iOnIVPaint(int nID, bool bActive, bool bInUse, void *pUserData);

	virtual void DoDataExchange(CDataExchange* pDX);    // DDX/DDV support
	virtual BOOL OnInitDialog();
	afx_msg void OnBnClickedBtnQuery();
	LRESULT OnPlateMessage(WPARAM wParam, LPARAM lParam);
	DECLARE_MESSAGE_MAP()

public:
	void SetLPRHandle( VzLPRClientHandle hLPRClient );
	void RecvPlateResult( TH_PlateResult *result, VZ_LPRC_RESULT_TYPE eResultType);
	void QueryByPage(int nPageIndex);

private:
	COleDateTime m_dtStart;
	COleDateTime m_dtStartTime;
	COleDateTime m_dtEnd;
	COleDateTime m_dtEndTime;
	CString m_strPlate;
	CListCtrl m_lstPlate;
	CString m_strPageMsg;

	CString m_strStartTime;
	CString m_strEndTime;

	int m_nTotalCount;
	int m_nCurPage;
	int m_nPageCount;

	BOOL m_bQueryOffline;

    
    unsigned char *m_pBufJpg;
    unsigned m_uSizeBufJpg;

    unsigned char *m_pBufRGB;
    unsigned m_uSizeBufRGB;

	VzLPRClientHandle m_hLPRClient;

    OutputWin m_winShow;

public:
	afx_msg void OnBnClickedBtnUp();
	afx_msg void OnBnClickedBtnDown();
	BOOL m_bOffline;
	afx_msg void OnBnClickedChkOffline();
    afx_msg void OnLvnItemchangedListPlate(NMHDR *pNMHDR, LRESULT *pResult);
};
