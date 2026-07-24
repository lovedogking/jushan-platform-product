#pragma once
#include <VzLPRClientSDK.h>
#include <VzLPRFaceClientSDK.h>

// DlgConfigExt dialog

class DlgConfigExt : public CDialog
{
	DECLARE_DYNAMIC(DlgConfigExt)

public:
	DlgConfigExt(VzLPRClientHandle handle, CWnd* pParent = NULL);   // standard constructor
	virtual ~DlgConfigExt();

// Dialog Data
	enum { IDD = IDD_DLG_PARAM_EXT };

private:
	VzLPRClientHandle m_hLPRC;
    unsigned m_uBitsRecType;
    unsigned m_uBitsTrigType;

protected:
	virtual void DoDataExchange(CDataExchange* pDX);    // DDX/DDV support

    void iOnCheckRecType(unsigned IDC, unsigned uBitRecType);

    void iOnCheckTrigType(unsigned IDC, unsigned uBitTrigType);

	void iOnCheckDrawMode( );

	BOOL GetIsCheckedByID( unsigned IDC );

	DECLARE_MESSAGE_MAP()
public:
	afx_msg void OnBnClickedBtnSetUserData();
	afx_msg void OnBnClickedBtnGetUserData();
    afx_msg void OnBnClickedRecTypeBlue();
    virtual BOOL OnInitDialog();
    afx_msg void OnBnClickedRecTypeYellow();
    afx_msg void OnBnClickedRecTypeBlack();
    afx_msg void OnBnClickedRecTypeCoach();
    afx_msg void OnBnClickedRecTypePolice();
    afx_msg void OnBnClickedRecTypeAmpol();
    afx_msg void OnBnClickedRecTypeArmy();
    afx_msg void OnBnClickedRecTypeGangao();
    afx_msg void OnBnClickedRecTypeEmbassy();
    afx_msg void OnBnClickedChkEnableTrigStable();
    afx_msg void OnBnClickedChkEnableTrigVloop();
    afx_msg void OnBnClickedChkEnableTrigIoIn1();
    afx_msg void OnBnClickedChkEnableTrigIoIn2();
	afx_msg void OnBnClickedChkPlatePos();
	afx_msg void OnBnClickedChkRealtimeResult();
	afx_msg void OnBnClickedChkVloopAndArea();
	afx_msg void OnBnClickedChkEnableTrigIoIn3();
	afx_msg void OnBnClickedRecTypeAviation();
	afx_msg void OnBnClickedRecTypeEnergy();
	afx_msg void OnBnClickedRecTypeEmergency();
	afx_msg void OnBnClickedRecTypeConsulate();
};
