package com.bimalghara.mp3downloader.presentation.home

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.bimalghara.mp3downloader.R
import com.bimalghara.mp3downloader.data.error.*
import com.bimalghara.mp3downloader.databinding.FragmentHomeBinding
import com.bimalghara.mp3downloader.presentation.base.BaseFragment
import com.bimalghara.mp3downloader.utils.*
import com.bimalghara.mp3downloader.utils.FileUtil.protectedDirectories
import com.bimalghara.mp3downloader.utils.permissions.PermissionManager
import com.bimalghara.mp3downloader.utils.permissions.Permissions
import com.google.android.material.progressindicator.CircularProgressIndicator.INDICATOR_DIRECTION_COUNTERCLOCKWISE
import com.google.android.material.progressindicator.CircularProgressIndicatorSpec
import com.google.android.material.progressindicator.IndeterminateDrawable
import dagger.hilt.android.AndroidEntryPoint


/**
 * Created by BimalGhara
 */

@AndroidEntryPoint
class HomeFragment : BaseFragment<FragmentHomeBinding>() {
    private val logTag = javaClass.simpleName

    private val homeViewModel: HomeViewModel by viewModels()
    private val permissionManager = PermissionManager.from(this)

    private var progressIndicatorDrawable: IndeterminateDrawable<CircularProgressIndicatorSpec>? = null


    private val startActivityForDirectoryPickUp =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val treeUri = result.data?.data
                Log.e(logTag, "selected treeUri: ${treeUri.toString()}")
                if (treeUri != null && context != null) {
                    val treeDocument:DocumentFile? = DocumentFile.fromTreeUri(context!!, treeUri)
                    Log.e(logTag, "selected treeDocument: ${treeDocument?.uri?.path}")
                    if(treeDocument != null){
                        Log.e(logTag, "selected treeDocument canWrite: ${treeDocument.canWrite()}")
                        if(treeDocument.canWrite()){
                            homeViewModel.setSelectedPath(treeDocument)
                        } else homeViewModel.showError(CustomException(cause = ERROR_WRITE_PERMISSION))
                    } else homeViewModel.showError(CustomException(cause = ERROR_SELECT_DIRECTORY_FAILED))
                } else homeViewModel.showError(CustomException(cause = ERROR_SELECT_DIRECTORY_FAILED))
            }
        }

    override fun getFragmentBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ) = FragmentHomeBinding.inflate(inflater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.etDestinationFolder.setOnClickListener {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                permissionManager
                .request(Permissions.Storage)
                .rationale("We need all Permissions to save file")
                .checkPermission { granted ->
                    if (granted) {
                        Log.e(logTag, "runtime permissions allowed")
                        startActivityDirectoryPickUp()
                    } else {
                        Log.e(logTag, "runtime permissions denied")
                        homeViewModel.showError(CustomException(cause = ERROR_NO_PERMISSION))
                    }
                }
            } else startActivityDirectoryPickUp()
        }

        binding.btnClearLink.setOnClickListener {
            binding.etLink.setText("")
        }

        context?.let { context ->
            val spec = CircularProgressIndicatorSpec(
                context, null, 0,
                com.google.android.material.R.style.Widget_Material3_CircularProgressIndicator_ExtraSmall
            ).apply {
                indicatorColors = intArrayOf(context.getColorFromAttr(com.google.android.material.R.attr.colorOnPrimary))
                trackThickness = context.resources.getDimension(R.dimen.track_thickness).toInt()
                indicatorDirection = INDICATOR_DIRECTION_COUNTERCLOCKWISE
            }
            progressIndicatorDrawable = IndeterminateDrawable.createCircularDrawable(context, spec)
        }
        binding.btnDownload.setOnClickListener {
            binding.root.hideKeyboard()
            context?.let { context ->
                homeViewModel.grabVideoInfo(context, binding.etLink.text)
            }
        }
    }


    override fun observeViewModel() {
        observeError(homeViewModel.errorSingleEvent)


        observe(homeViewModel.selectedPathLiveData) {
            Log.d(logTag, "observe selectedPathLiveData | $it")
            if(it?.uri?.path != null) {
                val folder = it.uri.path!!.split("/").last()
                if (protectedDirectories.contains(folder)) {
                    homeViewModel.setSelectedPath(null)
                    homeViewModel.showError(CustomException(cause = ERROR_PROTECTED_DIRECTORY))
                } else {
                    binding.etDestinationFolder.text = folder
                }
            }
        }

        observe(homeViewModel.videoDetailsLiveData) {
            Log.d(logTag, "observe usersLiveData | $it")
            when (it) {
                is ResourceWrapper.Loading -> {
                    disableInputs()
                }
                is ResourceWrapper.Success -> {
                    it.data!!.also { vd ->
                        vd.selectedUri = homeViewModel.selectedPathLiveData.value?.uri
                    }
                    findNavController().navigate(
                        HomeFragmentDirections.actionUsersFragmentToProcessFileFragment(it.data)
                    )
                }
                else -> {
                    enableInputs()
                }
            }
        }
    }

    private fun startActivityDirectoryPickUp() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        startActivityForDirectoryPickUp.launch(intent)
    }

    private fun enableInputs() {
        binding.btnDownload.icon = null
        context?.let { binding.btnDownload.text = it.getStringFromResource(R.string.download) }
        binding.etLink.isEnabled = true
        binding.etLink.alpha = 1F
        binding.etDestinationFolder.isEnabled = true
        binding.etDestinationFolder.alpha = 1F
        binding.btnDownload.isEnabled = true
        binding.btnDownload.isClickable = true
    }

    private fun disableInputs() {
        progressIndicatorDrawable?.let{ binding.btnDownload.icon = progressIndicatorDrawable }
        context?.let { binding.btnDownload.text = it.getStringFromResource(R.string.grabbing) }
        binding.etLink.isEnabled = false
        binding.etLink.alpha = 0.5F
        binding.etDestinationFolder.isEnabled = false
        binding.etDestinationFolder.alpha = 0.5F
        binding.btnDownload.isEnabled = false
        binding.btnDownload.isClickable = false
    }

}