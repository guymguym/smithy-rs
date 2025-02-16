/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

#![allow(dead_code)]

//! Tower middleware service for creating presigned requests

use crate::presigning::PresignedRequest;
use aws_smithy_http::operation;
use http::header::USER_AGENT;
use std::future::{ready, Ready};
use std::marker::PhantomData;
use std::task::{Context, Poll};

/// Tower [`Service`](tower::Service) for generated a [`PresignedRequest`] from the AWS middleware.
#[derive(Default, Debug)]
#[non_exhaustive]
pub(crate) struct PresignedRequestService<E> {
    _phantom: PhantomData<E>,
}

// Required because of the derive Clone on MapRequestService.
// Manually implemented to avoid requiring errors to implement Clone.
impl<E> Clone for PresignedRequestService<E> {
    fn clone(&self) -> Self {
        Self {
            _phantom: Default::default(),
        }
    }
}

impl<E> PresignedRequestService<E> {
    /// Creates a new `PresignedRequestService`
    pub(crate) fn new() -> Self {
        Self {
            _phantom: Default::default(),
        }
    }
}

impl<E> tower::Service<operation::Request> for PresignedRequestService<E> {
    type Response = PresignedRequest;
    type Error = E;
    type Future = Ready<Result<PresignedRequest, E>>;

    fn poll_ready(&mut self, _cx: &mut Context<'_>) -> Poll<Result<(), Self::Error>> {
        Poll::Ready(Ok(()))
    }

    fn call(&mut self, req: operation::Request) -> Self::Future {
        let (mut req, _) = req.into_parts();

        // Remove user agent headers since the request will not be executed by the AWS Rust SDK.
        req.headers_mut().remove(USER_AGENT);
        req.headers_mut().remove("X-Amz-User-Agent");

        ready(Ok(PresignedRequest::new(req.map(|_| ()))))
    }
}
