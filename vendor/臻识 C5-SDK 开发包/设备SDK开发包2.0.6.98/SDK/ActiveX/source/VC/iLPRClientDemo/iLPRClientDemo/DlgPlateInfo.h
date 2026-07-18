#pragma once


// CDlgPlateInfo dialog

class CDlgPlateInfo : public CDialog
{
	DECLARE_DYNAMIC(CDlgPlateInfo)

public:
	CDlgPlateInfo(CWnd* pParent = NULL);   // standard constructor
	virtual ~CDlgPlateInfo();

// Dialog Data
	enum { IDD = IDD_DLG_PLATE_IFNO };

protected:
	virtual void DoDataExchange(CDataExchange* pDX);    // DDX/DDV support

	DECLARE_MESSAGE_MAP()

public:
	void SetPlateInfo( CString strPlate, BOOL bEnable, COleDateTime dt, BOOL bAlarm, CString strComment );
	void GetPlateInfo( CString &strPlate, BOOL &bEnable, COleDateTime &dt, BOOL &bAlarm, CString &strComment );

public:
	CString m_strPlate;
	BOOL m_bEnable;
	COleDateTime m_dtDate;
	BOOL m_bAlarm;
	afx_msg void OnBnClickedOk();
	CString m_strComment;
	afx_msg void OnEnChangeEdit1();
};
