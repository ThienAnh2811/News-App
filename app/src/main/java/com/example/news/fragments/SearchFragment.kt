package com.example.news.fragments

import android.content.Context
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.widget.AbsListView
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.Observer
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.news.R
import com.example.news.databinding.FragmentSearchBinding
import com.example.news.adapters.NewsAdapter
import com.example.news.ui.NewsActivity
import com.example.news.ui.NewsViewModels
import com.example.news.util.Constants
import com.example.news.util.Constants.Companion.SEARCH_TIME_DELAY
import com.example.news.util.Resource
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


private lateinit var binding: FragmentSearchBinding
class SearchFragment : Fragment(R.layout.fragment_search) {

    lateinit var newsViewModels: NewsViewModels
    lateinit var newsAdapter: NewsAdapter
    lateinit var retryButton: Button
    lateinit var errorText: TextView
    lateinit var itemSearchError: CardView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding = FragmentSearchBinding.bind(view)


        itemSearchError = view.findViewById(R.id.itemHeadlinesError)

        val inflater = requireContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val view: View = inflater.inflate(R.layout.item_errors, null)

        retryButton = view.findViewById(R.id.retryButton)
        errorText = view.findViewById(R.id.errorText)
        newsViewModels = (activity as NewsActivity).newsViewModels
        setUpSearchRecycler()

        newsAdapter.setOnItemClickListener {
            val bundle = Bundle().apply {
                putSerializable("article", it)
            }
            findNavController().navigate(R.id.action_searchFragment_to_articleFragment)
        }

        var job: Job? = null
        binding.searchEdit.addTextChangedListener(){
            editable ->
            job?.cancel()
            job = MainScope().launch {
                delay(SEARCH_TIME_DELAY)
                editable?.let{
                    if(editable.toString().isNotEmpty()){
                        newsViewModels.searchNews(editable.toString())
                    }
                }
            }
        }


        newsViewModels.searchNews.observe(viewLifecycleOwner, Observer {
                response -> when(response){
            is Resource.Success<*> ->{
                hideProgressBar()
                hideErrorMessage()
                response.data?.let {
                        newsResponse ->
                    newsAdapter.differ.submitList(newsResponse.articles.toList())
                    val totalPages = newsResponse.totalResults / Constants.PAGE_SIZE + 2
                    isLastPage = newsViewModels.searchNewsPage == totalPages
                    if(isLastPage){
                        binding.recyclerSearch.setPadding(0, 0, 0, 0)
                    }
                }
            }
            is Resource.Error<*> ->{
                hideProgressBar()
                response.message?.let {
                        message -> Toast.makeText(activity, "Error: $message", Toast.LENGTH_SHORT).show()
                    showErrorMessage(message)
                }
            }
            is Resource.Loading<*> ->{
                showProgressBar()
            }
        }
        })

        retryButton.setOnClickListener{
            if(binding.searchEdit.text.toString().isNotEmpty()){
                newsViewModels.searchNews(binding.searchEdit.text.toString())
            }else{
                hideErrorMessage()
            }
        }
    }

    var isError = false
    var isLoading = false
    var isLastPage = false
    var isScrolling = false

    private fun hideProgressBar(){
        binding.paginationProgressBar.visibility = View.INVISIBLE
        isLoading = false
    }
    private fun showProgressBar(){
        binding.paginationProgressBar.visibility = View.VISIBLE
        isLoading = true
    }
    private fun hideErrorMessage(){
        itemSearchError.visibility = View.INVISIBLE
        isError = false
    }

    private fun showErrorMessage(message: String){
        itemSearchError.visibility = View.VISIBLE
        errorText.text = message
        isError = true
    }

    val scrollListener = object : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            super.onScrolled(recyclerView, dx, dy)
            val layoutManager = recyclerView.layoutManager as LinearLayoutManager
            val firstVisibleItemPostion = layoutManager.findFirstVisibleItemPosition()
            val totalItemCount = layoutManager.itemCount
            val isNoErrors = !isError
            val isNotLoadingAndNotLastPage = !isLoading && isLastPage
            val isNotAtBeginning = firstVisibleItemPostion >= 0
            val isTotalMoreThanVisible = totalItemCount >= com.example.news.util.Constants.PAGE_SIZE
            val shouldPaginate = isNoErrors && isNotLoadingAndNotLastPage && isNotAtBeginning
                    && isTotalMoreThanVisible && isScrolling
            if (shouldPaginate) {
                newsViewModels.searchNews(binding.searchEdit.text.toString())
                isScrolling = false
            }
        }

        override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
            super.onScrollStateChanged(recyclerView, newState)

            if (newState == AbsListView.OnScrollListener.SCROLL_STATE_TOUCH_SCROLL) {
                isScrolling = true
            }
        }
    }

    private fun setUpSearchRecycler(){
        newsAdapter = NewsAdapter()
        binding.recyclerSearch.apply {
            adapter = newsAdapter
            layoutManager = LinearLayoutManager(activity)
            addOnScrollListener(this@SearchFragment.scrollListener)
        }
    }

}